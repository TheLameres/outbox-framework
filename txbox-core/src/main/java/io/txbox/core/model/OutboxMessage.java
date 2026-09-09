package io.txbox.core.model;

import java.time.Instant;
import java.util.*;

/**
 * Исходящее сообщение outbox. Immutable record — equals/hashCode/toString/геттеры
 * генерируются компилятором; Lombok не нужен.
 *
 * <p>Компактный конструктор выполняет валидацию и нормализацию при ЛЮБОМ способе
 * создания записи, включая десериализацию Jackson 3.
 */
public record OutboxMessage(
        UUID messageId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        SequencedMap<String, String> headers,
        Instant timestamp,   // occurredAt
        int attempt
) implements Message {

    public OutboxMessage {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(timestamp, "timestamp");
        aggregateType = requireText(aggregateType, "aggregateType");
        aggregateId = requireText(aggregateId, "aggregateId");
        eventType = requireText(eventType, "eventType");
        Objects.requireNonNull(payload, "payload");
        if (attempt < 0) throw new IllegalArgumentException("attempt must be >= 0, got " + attempt);
        headers = headers == null
                ? new LinkedHashMap<>()
                : Collections.unmodifiableSequencedMap(new LinkedHashMap<>(headers));
    }

    // ── фабрики ──────────────────────────────────────────────────────────────

    public static OutboxMessage of(String aggregateType, String aggregateId,
                                   String eventType, String payload) {
        return new OutboxMessage(
                UUID.randomUUID(), aggregateType, aggregateId,
                eventType, payload, null, Instant.now(), 0);
    }

    // ── wither-методы (immutable copies) ─────────────────────────────────────

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    public OutboxMessage withHeader(String key, String value) {
        SequencedMap<String, String> next = new LinkedHashMap<>(headers);
        next.putLast(key, value);
        return new OutboxMessage(messageId, aggregateType, aggregateId,
                eventType, payload, next, timestamp, attempt);
    }

    public OutboxMessage withHeaders(Map<String, String> extra) {
        SequencedMap<String, String> next = new LinkedHashMap<>(headers);
        next.putAll(extra);
        return new OutboxMessage(messageId, aggregateType, aggregateId,
                eventType, payload, next, timestamp, attempt);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    public OutboxMessage nextAttempt() {
        return new OutboxMessage(messageId, aggregateType, aggregateId,
                eventType, payload, headers, timestamp, attempt + 1);
    }
}
