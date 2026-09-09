package io.txbox.examples.event;

import io.txbox.core.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public sealed interface OrderEvents extends DomainEvent {
    record OrderCreated(UUID id,
                        BigDecimal sum,
                        String productName,
                        Instant occurredAt) implements OrderEvents {
    }
}
