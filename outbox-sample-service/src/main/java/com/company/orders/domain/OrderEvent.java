package com.company.orders.domain;

import com.company.outbox.core.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Sealed-иерархия доменных событий из records.
 *
 * <p>Что это даёт по сравнению с Lombok-классами:
 * <ul>
 *   <li>exhaustive switch — компилятор не даст забыть новый тип события;</li>
 *   <li>Jackson 3 сериализует records без {@code @Jacksonized};</li>
 *   <li>валидация в компактном конструкторе работает и при десериализации.</li>
 * </ul>
 */
public sealed interface OrderEvent extends DomainEvent {

    UUID orderId();

    record Created(UUID orderId, UUID customerId, BigDecimal total, Instant occurredAt)
            implements OrderEvent {

        public Created {
            if (total.signum() <= 0) {
                throw new IllegalArgumentException("total must be positive, got " + total);
            }
        }

        public static Created now(UUID orderId, UUID customerId, BigDecimal total) {
            return new Created(orderId, customerId, total, Instant.now());
        }
    }

    record Paid(UUID orderId, BigDecimal amount, String paymentId, Instant occurredAt)
            implements OrderEvent {}

    record Shipped(UUID orderId, String trackingNumber, Instant occurredAt)
            implements OrderEvent {}

    record Cancelled(UUID orderId, String reason, boolean refundRequired, Instant occurredAt)
            implements OrderEvent {}
}
