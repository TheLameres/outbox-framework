package io.txbox.examples.event;

import io.txbox.core.event.DomainEvent;
import io.txbox.examples.dto.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public sealed interface OrderEvents {

    @DomainEvent
    record OrderCreated(UUID id,
                        BigDecimal sum,
                        String productName,
                        Instant createdAt) implements OrderEvents {
    }

    @DomainEvent
    record OrderChangeStatus(UUID id,
                             OrderStatus status,
                             Instant updatedAt) implements OrderEvents {}
}
