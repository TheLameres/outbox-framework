package io.txbox.producer.kafka;

import io.txbox.core.model.OutboxMessage;
import io.txbox.core.routing.DestinationResolver;
import io.txbox.kafka.converter.OutboxMessageKafkaConverter;
import io.txbox.producer.api.OutboxPublisher;
import io.txbox.producer.api.PublishOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Kafka-реализация OutboxPublisher.
 * Использует OutboxMessageKafkaConverter (txbox-kafka) для маппинга заголовков.
 * Блокирующий get() на виртуальном потоке не занимает платформенный поток —
 * поэтому параллельная публикация батча безопасна.
 */
@Slf4j
@RequiredArgsConstructor
public class KafkaOutboxPublisher implements OutboxPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DestinationResolver destinationResolver;
    private final Duration sendTimeout;

    @Override
    public PublishOutcome publish(OutboxMessage message) {
        String topic = destinationResolver.resolve(message);
        long startNs = System.nanoTime();

        var record = OutboxMessageKafkaConverter.toProducerRecord(message, topic);

        try {
            kafkaTemplate.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return new PublishOutcome.Published(
                    message.messageId(), topic,
                    Duration.ofNanos(System.nanoTime() - startNs));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PublishOutcome.Retryable(message.messageId(), "interrupted", e);

        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return classifyKafkaError(message.messageId(), cause);

        } catch (java.util.concurrent.TimeoutException e) {
            return new PublishOutcome.Retryable(message.messageId(), "send timeout", e);
        }
    }

    private static PublishOutcome classifyKafkaError(java.util.UUID messageId, Throwable cause) {
        return switch (cause) {
            case org.apache.kafka.common.errors.RetriableException r ->
                    new PublishOutcome.Retryable(messageId, "kafka retriable: " + r.getMessage(), r);
            case org.apache.kafka.common.errors.RecordTooLargeException r ->
                    new PublishOutcome.Fatal(messageId, "record too large", r);
            case org.apache.kafka.common.errors.SerializationException r ->
                    new PublishOutcome.Fatal(messageId, "serialization failed", r);
            default ->
                    new PublishOutcome.Retryable(messageId, cause.getMessage(), cause);
        };
    }
}
