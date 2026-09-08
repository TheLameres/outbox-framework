package io.txbox.core.model;

import java.time.Instant;
import java.util.*;

/**
 * Входящее сообщение inbox. Immutable record.
 *
 * <p>{@code messageId} — берётся из заголовка {@code txbox-message-id} или
 * вычисляется детерминированно как UUID.nameUUIDFromBytes("partition:offset").
 * <p>{@code partition} + {@code offset} — ключи дедупликации; уникальный индекс
 * в БД строится по ним, а не только по messageId.
 */
public record InboxMessage(
        UUID messageId,
        String source,       // aggregateType от producer (из header)
        String sourceId,     // aggregateId от producer (из kafka key)
        String eventType,
        String payload,
        SequencedMap<String, String> headers,
        Instant timestamp,   // occurredAt из header
        Instant receivedAt,  // системное время получения
        int partition,       // для дедупликации
        long offset          // для дедупликации
) implements Message {

    public InboxMessage {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(receivedAt, "receivedAt");
        eventType = requireText(eventType, "eventType");
        payload   = requireText(payload,   "payload");
        headers = headers == null
                ? Collections.unmodifiableSequencedMap(new LinkedHashMap<>())
                : Collections.unmodifiableSequencedMap(new LinkedHashMap<>(headers));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
