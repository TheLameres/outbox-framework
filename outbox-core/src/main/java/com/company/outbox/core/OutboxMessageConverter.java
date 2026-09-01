package com.company.outbox.core;

/** Доменное событие -> OutboxMessage. Позволяет кастомную маршрутизацию и headers. */
@FunctionalInterface
public interface OutboxMessageConverter<E extends DomainEvent> {
    OutboxMessage convert(E event);
}
