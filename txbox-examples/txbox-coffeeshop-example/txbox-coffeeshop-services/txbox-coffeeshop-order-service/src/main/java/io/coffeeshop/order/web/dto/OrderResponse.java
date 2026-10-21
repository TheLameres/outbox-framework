package io.coffeeshop.order.web.dto;

import io.coffeeshop.core.domain.Order;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(UUID id,
                            String status,
                            MoneyDto total,
                            Instant placedAt,
                            String failureReason) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.status().name(),
                MoneyDto.from(order.total()),
                order.placedAt(),
                order.failureReason());
    }
}
