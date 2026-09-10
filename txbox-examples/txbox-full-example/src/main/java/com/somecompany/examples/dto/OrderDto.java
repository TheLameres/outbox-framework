package com.somecompany.examples.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderDto(
        UUID id,
        BigDecimal sum,
        String productName,
        Instant createdAt,
        OrderStatus status
) {
}
