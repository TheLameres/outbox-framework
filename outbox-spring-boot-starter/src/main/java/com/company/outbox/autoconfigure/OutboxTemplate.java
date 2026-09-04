package com.company.outbox.autoconfigure;

import com.company.outbox.core.DomainEvent;
import com.company.outbox.core.OutboxMessage;
import com.company.outbox.core.OutboxMessageConverter;
import com.company.outbox.core.OutboxStore;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Единственный API, который видит бизнес-код.
 *
 * <p>Jackson 3 (Spring Boot 4) — пакет {@code tools.jackson}, а исключения
 * стали unchecked, поэтому {@code @SneakyThrows} и try/catch вокруг
 * {@code writeValueAsString} больше не нужны.
 */
@RequiredArgsConstructor
public class OutboxTemplate {

    private final OutboxStore store;
    private final ObjectMapper objectMapper;

    /** Публикация доменного события. Обязательно внутри активной транзакции. */
    public void publish(DomainEvent event, String aggregateType, String aggregateId) {
        store.save(toMessage(event, aggregateType, aggregateId));
    }

    /** Несколько событий одной транзакции — одной batch-вставкой. */
    public void publishAll(List<? extends DomainEvent> events,
                           String aggregateType,
                           String aggregateId) {
        store.saveAll(events.stream()
                .map(e -> toMessage(e, aggregateType, aggregateId))
                .toList());
    }

    /** Полный контроль над маршрутизацией и заголовками. */
    public <E extends DomainEvent> void publish(E event, OutboxMessageConverter<E> converter) {
        store.save(converter.convert(event));
    }

    private OutboxMessage toMessage(DomainEvent event, String aggregateType, String aggregateId) {
        return OutboxMessage
                .of(aggregateType, aggregateId, event.eventType(), objectMapper.writeValueAsString(event))
                .withHeader("content-type", "application/json")
                .withHeader("schema-version", "1")
                .withHeader("occurred-at", event.occurredAt().toString());
    }
}
