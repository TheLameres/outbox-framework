package io.txbox.consumer.kafka.listener;

import io.txbox.consumer.kafka.configuration.InboxConfiguration;
import io.txbox.consumer.kafka.dispatcher.InboxEventDispatcher;
import io.txbox.consumer.model.InboundEventContext;
import io.txbox.consumer.outcome.ProcessingOutcome;
import io.txbox.consumer.store.InboxStore;
import io.txbox.core.model.InboxMessage;
import io.txbox.kafka.header.TxBoxHeaders;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.SequencedMap;
import java.util.UUID;

/**
 * Kafka-слушатель входящих событий.
 *
 * <p>Зависит только от txbox-consumer-api, txbox-consumer-jpa, txbox-kafka.
 * Не зависит от txbox-consumer-starter — конфигурация приходит через
 * property-placeholders в @KafkaListener, containerFactory задаётся снаружи.
 *
 * <p>Цикл обработки (одна TX):
 * <ol>
 *   <li>Дедупликация по (partition, offset)</li>
 *   <li>Сборка InboxMessage из ConsumerRecord через TxBoxHeaders</li>
 *   <li>save → dispatch → applyOutcome</li>
 *   <li>ack только при Success / Fatal / Skipped; Retryable — без ack</li>
 * </ol>
 */
@Slf4j
public class InboxKafkaListener {

    private final InboxStore store;
    private final InboxEventDispatcher dispatcher;


    public InboxKafkaListener(InboxStore store, InboxEventDispatcher dispatcher) {
        this.store = store;
        this.dispatcher = dispatcher;
    }

    private static UUID parseUUID(String value, int partition, long offset) {
        if (value != null && !value.isBlank()) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return UUID.nameUUIDFromBytes(
                (partition + ":" + offset).getBytes(StandardCharsets.UTF_8));
    }

    private static Instant parseInstant(String value) {
        if (value != null && !value.isBlank()) {
            try {
                return Instant.parse(value);
            } catch (Exception ignored) {
            }
        }
        return Instant.now();
    }

    @Transactional
    public void listen(ConsumerRecord<String, String> record, Acknowledgment ack) {
        int partition = record.partition();
        long offset = record.offset();

        try {
            // 1. Дедупликация
            // Пропускаем только ЗАВЕРШЁННЫЕ записи. Запись в RECEIVED (предыдущий исход был
            // Retryable, TX закоммитилась без ack) — это недоделанная обработка, а не дубликат:
            // её нужно выполнить заново, иначе сообщение будет потеряно.
            var existing = store.findByKafkaPosition(partition, offset);
            if (existing.isPresent() && store.wasProcessed(existing.get().messageId())) {
                log.debug("Inbox duplicate skipped: partition={}, offset={}", partition, offset);
                ack.acknowledge();
                return;
            }

            // 2. Извлекаем заголовки
            SequencedMap<String, String> headers = new LinkedHashMap<>();
            record.headers().forEach(h ->
                    headers.put(h.key(), new String(h.value(), StandardCharsets.UTF_8)));

            // 3. Читаем txbox-метаданные через TxBoxHeaders-константы
            UUID messageId = parseUUID(headers.get(TxBoxHeaders.MESSAGE_ID), partition, offset);
            String source = headers.getOrDefault(TxBoxHeaders.AGGREGATE_TYPE, record.topic());
            String sourceId = record.key() != null ? record.key() : "";
            String eventType = headers.getOrDefault(TxBoxHeaders.EVENT_TYPE, "Unknown");
            Instant occurredAt = parseInstant(headers.get(TxBoxHeaders.OCCURRED_AT));
            Instant receivedAt = Instant.ofEpochMilli(record.timestamp());

            // 4. Собираем InboxMessage
            InboxMessage message = new InboxMessage(
                    messageId, source, sourceId, eventType,
                    record.value(), headers,
                    occurredAt, receivedAt,
                    partition, offset);

            InboundEventContext context = new InboundEventContext(
                    messageId, source, sourceId, eventType, headers,
                    occurredAt, receivedAt);

            // 5. Дедупликация по messageId: то же событие под другим (partition, offset) —
            //    ребаланс, повторная публикация после reclaimStale. Позиционная проверка выше
            //    такой дубликат не ловит. true только для ЗАВЕРШЁННЫХ записей.
            boolean alreadyProcessed = store.wasProcessed(messageId);

            // 6. Сохраняем как RECEIVED в той же TX, что и handler
            //    (save() идемпотентен: по позиции и по messageId)
            store.save(message);

            // 7. Диспетчеризуем
            ProcessingOutcome outcome = dispatcher.dispatch(message, context, alreadyProcessed);

            // 8. Применяем исход
            applyOutcome(outcome, ack, alreadyProcessed);

        } catch (Exception e) {
            log.error("Inbox processing failed: partition={}, offset={}", partition, offset, e);
            throw e; // Spring Kafka NACK и retry
        }
    }

    private void applyOutcome(ProcessingOutcome outcome, Acknowledgment ack,
                              boolean alreadyProcessed) {
        if (alreadyProcessed) {
            // Исходная запись уже в терминальном статусе — НЕ перезаписываем её
            // (иначе PROCESSED превратится в SKIPPED/FAILED). Только решаем судьбу offset.
            if (outcome instanceof ProcessingOutcome.Retryable r) {
                // Упал handler с idempotent=false — повторяем доставку
                log.warn("Duplicate event retryable: messageId={}, reason={}",
                        r.messageId(), r.reason());
            } else {
                log.debug("Duplicate event acknowledged: messageId={}", outcome.messageId());
                ack.acknowledge();
            }
            return;
        }
        switch (outcome) {
            case ProcessingOutcome.Success s -> {
                store.markProcessed(s.messageId());
                ack.acknowledge();
            }
            case ProcessingOutcome.Fatal f -> {
                store.markFailed(f.messageId(), f.reason());
                log.error("Event fatal: messageId={}, reason={}", f.messageId(), f.reason(), f.cause());
                ack.acknowledge(); // commit offset — DLQ через error handler в starter
            }
            case ProcessingOutcome.Skipped sk -> {
                store.markSkipped(sk.messageId(), sk.reason());
                ack.acknowledge();
            }
            case ProcessingOutcome.Retryable r ->
                // Без ack — Spring Kafka NACK и повторит
                    log.warn("Event retryable: messageId={}, reason={}", r.messageId(), r.reason());
        }
    }
}
