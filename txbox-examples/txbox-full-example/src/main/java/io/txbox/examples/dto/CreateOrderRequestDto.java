package io.txbox.examples.dto;

import java.math.BigDecimal;

public record CreateOrderRequestDto(
        BigDecimal sum,
        String productName
) {
}
