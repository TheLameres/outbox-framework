package com.company.outbox.core;

import java.time.Instant;
import java.util.*;

/**
 * Сообщение outbox. Полностью иммутабельный record — Lombok {@code @Value/@Builder}
 * больше не нужен: equals/hashCode/toString/геттеры генерирует компилятор.
 *
 * <p>Jackson 3 (Spring Boot 4.x) десериализует records из коробки —
 * {@code @Jacksonized} не требуется.
 */
public record OutboxMessage(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        SequencedMap<String, String> headers,
        Instant createdAt,
        int attempt
) {

    /**
     * Компактный конструктор: единственное место валидации и нормализации.
     * Выполняется при ЛЮБОМ способе создания record, включая десериализацию.
     */
    public OutboxMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        aggregateType = requireText(aggregateType, "aggregateType");
        aggregateId = requireText(aggregateId, "aggregateId");
        eventType = requireText(eventType, "eventType");
        Objects.requireNonNull(payload, "payload");
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must be >= 0, got " + attempt);
        }
        // LinkedHashMap сохраняет порядок заголовков; SequencedMap — новый интерфейс Java 21
        headers = headers == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(headers);
    }

    /** Фабрика для типового случая. */
    public static OutboxMessage of(String aggregateType,
                                   String aggregateId,
                                   String eventType,
                                   String payload) {
        return new OutboxMessage(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                eventType,
                payload,
                new LinkedHashMap<>(),
                Instant.now(),
                0
        );
    }

    /** «Wither» вместо builder-а — иммутабельная копия с добавленным заголовком. */
    public OutboxMessage withHeader(String key, String value) {
        SequencedMap<String, String> next = new LinkedHashMap<>(headers);
        next.putLast(key, value); // putLast — API SequencedMap из Java 21
        return new OutboxMessage(
                id, aggregateType, aggregateId, eventType, payload, next, createdAt, attempt);
    }

    public OutboxMessage withHeaders(Map<String, String> extra) {
        SequencedMap<String, String> next = new LinkedHashMap<>(headers);
        next.putAll(extra);
        return new OutboxMessage(
                id, aggregateType, aggregateId, eventType, payload, next, createdAt, attempt);
    }

    public OutboxMessage nextAttempt() {
        return new OutboxMessage(
                id, aggregateType, aggregateId, eventType, payload, headers, createdAt, attempt + 1);
    }

    /** Возвращает неизменяемое представление заголовков для внешних потребителей. */
    @Override
    public SequencedMap<String, String> headers() {
        return java.util.Collections.unmodifiableSequencedMap(headers);
    }

    /** Возраст сообщения — основа метрики outbox lag. */
    public java.time.Duration age() {
        return java.time.Duration.between(createdAt, Instant.now());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
