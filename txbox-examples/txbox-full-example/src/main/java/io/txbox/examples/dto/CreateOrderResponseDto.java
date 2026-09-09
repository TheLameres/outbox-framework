package io.txbox.examples.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateOrderResponseDto(
        UUID id,
        BigDecimal sum,
        String productName,
        Instant occurredAt
) {
}
