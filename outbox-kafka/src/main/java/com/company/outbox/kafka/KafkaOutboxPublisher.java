package com.company.outbox.kafka;

import com.company.outbox.core.DestinationResolver;
import com.company.outbox.core.OutboxMessage;
import com.company.outbox.core.OutboxPublisher;
import com.company.outbox.core.PublishOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class KafkaOutboxPublisher implements OutboxPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DestinationResolver destinationResolver;
    private final Duration sendTimeout;

    @Override
    public PublishOutcome publish(OutboxMessage message) {
        String topic = destinationResolver.resolve(message);
        long startedAt = System.nanoTime();

        var record = new ProducerRecord<>(topic, message.aggregateId(), message.payload());
        addHeader(record, "outbox-message-id", message.id().toString());
        addHeader(record, "outbox-event-type", message.eventType());
        addHeader(record, "outbox-aggregate-type", message.aggregateType());
        addHeader(record, "outbox-attempt", Integer.toString(message.attempt()));
        message.headers().forEach((key, value) -> addHeader(record, key, value));

        try {
            // Блокирующий get() на виртуальном потоке не занимает платформенный поток —
            // поэтому в поллере безопасно публиковать сотни сообщений параллельно.
            kafkaTemplate.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return new PublishOutcome.Published(
                    message.id(), topic, Duration.ofNanos(System.nanoTime() - startedAt));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PublishOutcome.Retryable(message.id(), "interrupted", e);

        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            // Классификация ошибок Kafka: что повторять, а что — сразу в DLQ
            return switch (cause) {
                case org.apache.kafka.common.errors.RetriableException retriable ->
                        new PublishOutcome.Retryable(message.id(), "kafka retriable", retriable);
                case org.apache.kafka.common.errors.RecordTooLargeException tooLarge ->
                        new PublishOutcome.Fatal(message.id(), "record too large", tooLarge);
                case org.apache.kafka.common.errors.SerializationException serde ->
                        new PublishOutcome.Fatal(message.id(), "serialization failed", serde);
                default ->
                        new PublishOutcome.Retryable(message.id(), cause.getMessage(), cause);
            };

        } catch (java.util.concurrent.TimeoutException e) {
            return new PublishOutcome.Retryable(message.id(), "send timeout", e);
        }
    }

    private static void addHeader(ProducerRecord<String, String> record, String key, String value) {
        if (value != null) {
            record.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
