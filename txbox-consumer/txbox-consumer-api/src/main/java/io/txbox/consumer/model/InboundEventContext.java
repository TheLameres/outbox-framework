package io.txbox.consumer.model;

import java.time.Instant;
import java.util.SequencedMap;
import java.util.UUID;

/**
 * Контекст входящего события — передаётся в handler-метод наряду с десериализованным event.
 * Позволяет осуществить доступ к метаданным (partition/offset, headers, timestamp).
 */
public record InboundEventContext(
        UUID messageId,
        String source,           // aggregateType от producer
        String sourceId,         // aggregateId от producer (partition key)
        String eventType,
        SequencedMap<String, String> headers,
        Instant occurredAt,      // timestamp event-generation на producer
        Instant receivedAt       // timestamp Kafka delivery
) {
    /**
     * Удобный хелпер: получить заголовок по ключу.
     */
    public String header(String key) {
        return headers.get(key);
    }
}
