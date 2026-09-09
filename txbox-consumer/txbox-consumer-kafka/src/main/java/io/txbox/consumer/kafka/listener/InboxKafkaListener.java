package io.txbox.consumer.kafka.listener;

import io.txbox.consumer.kafka.dispatcher.InboxEventDispatcher;
import io.txbox.consumer.model.InboundEventContext;
import io.txbox.consumer.outcome.ProcessingOutcome;
import io.txbox.consumer.store.InboxStore;
import io.txbox.core.model.InboxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Kafka-слушатель входящих событий.
 * Цикл обработки:
 * 1. Получить сообщение из Kafka (deserialize по type header)
 * 2. Проверить дедупликацию (partition:offset)
 * 3. Найти и вызвать подходящий handler-метод (@InboxEventHandler)
 * 4. Применить исход (commit/NACK, обновить inbox status)
 *
 * <p>Все операции В ОДНОЙ TX: save inbox record + handler exec + mark status
 * → либо всё успешно, либо всё откатывается.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InboxKafkaListener {

    private final InboxStore store;
    private final InboxEventDispatcher dispatcher;
    private final InboxProperties properties;

    /**
     * Основной Kafka listener.
     * Обработка в TX: если произойдёт исключение — откатнётся и консьюмер NACK'нет сообщение.
     */
    @Transactional
    @KafkaListener(
            topics = "${txbox.consumer.kafka.topic}",
            groupId = "${txbox.consumer.kafka.group-id}",
            containerFactory = "inboxListenerContainerFactory"
    )
    public void listen(
            @Payload(required = false) String rawPayload,
            @Header(name = KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(name = KafkaHeaders.OFFSET) long offset,
            @Header(name = "txbox-message-id") UUID messageId,
            @Header(name = "txbox-aggregate-type") String source,
            @Header(name = "txbox-aggregate-id") String sourceId,
            @Header(name = "txbox-event-type") String eventType,
            @Header(name = KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Map<String, Object> headers,
            Acknowledgment ack
    ) {
        try {
            // Дедупликация: уже видели это сообщение?
            if (store.findByKafkaPosition(partition, offset).isPresent()) {
                log.debug("Inbox duplicate skipped: partition={}, offset={}", partition, offset);
                ack.acknowledge();
                return;
            }

            // Создаём InboxMessage и контекст
            SequencedMap<String, String> contextHeaders = new LinkedHashMap<>();
            headers.forEach((k, v) -> contextHeaders.put(k, String.valueOf(v)));

            Instant receivedAt = Instant.ofEpochMilli(timestamp);
            InboxMessage message = new InboxMessage(
                    messageId, source, sourceId, eventType, rawPayload, contextHeaders,
                    receivedAt, receivedAt, partition, offset
            );

            InboundEventContext context = new InboundEventContext(
                    messageId, source, sourceId, eventType, contextHeaders,
                    receivedAt, receivedAt
            );

            // Сохраняем в inbox как RECEIVED (в той же TX что ниже)
            store.save(message);

            // Диспетчеризируем к handler-у
            ProcessingOutcome outcome = dispatcher.dispatch(message, context);

            // Применяем исход
            applyOutcome(outcome);

            // Commit offset
            ack.acknowledge();

        } catch (Exception e) {
            // Исключение вне handler'а — это infrastructure failure
            // NACK и retry позже
            log.error("Inbox processing failed: partition={}, offset={}", partition, offset, e);
            throw e;  // Spring Kafka NACK'нет и перезапустит консьюмер
        }
    }

    /**
     * Применяет исход обработки события к inbox-хранилищу.
     * Используется классификатор для преобразования исключений.
     */
    private void applyOutcome(ProcessingOutcome outcome) {
        switch (outcome) {
            case ProcessingOutcome.Success s ->
                    store.markProcessed(s.messageId());

            case ProcessingOutcome.Retryable r ->
                    log.warn("Event retryable: {} - {}", r.messageId(), r.reason());
                    // Оставляем RECEIVED, не обновляем — retry позже через scheduled retry job

            case ProcessingOutcome.Fatal f -> {
                store.markFailed(f.messageId(), f.reason());
                log.error("Event fatal: {} - {}", f.messageId(), f.reason(), f.cause());
                // Отправить в DLQ вызывает producer в starter-конфиге
            }

            case ProcessingOutcome.Skipped sk ->
                    store.markSkipped(sk.messageId(), sk.reason());
        }
    }
}
