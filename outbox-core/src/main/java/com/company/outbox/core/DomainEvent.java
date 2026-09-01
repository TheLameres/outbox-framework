package com.company.outbox.core;

import java.time.Instant;

/**
 * Маркер доменного события. Наследники в сервисах объявляются как
 * sealed-интерфейс + records — это даёт exhaustive switch при маршрутизации.
 *
 * <pre>{@code
 * public sealed interface OrderEvent extends DomainEvent {
 *     record Created(UUID orderId, BigDecimal total, Instant occurredAt) implements OrderEvent {}
 *     record Paid(UUID orderId, BigDecimal amount, Instant occurredAt)   implements OrderEvent {}
 *     record Cancelled(UUID orderId, String reason, Instant occurredAt)  implements OrderEvent {}
 * }
 * }</pre>
 */
public interface DomainEvent {

    Instant occurredAt();

    /** Имя типа события по умолчанию = простое имя record-а. */
    default String eventType() {
        return getClass().getSimpleName();
    }
}
