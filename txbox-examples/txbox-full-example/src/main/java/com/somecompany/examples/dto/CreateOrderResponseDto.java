package com.somecompany.examples.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateOrderResponseDto(
        UUID id,
        Instant createdAt,
        OrderStatus status
) {
}
