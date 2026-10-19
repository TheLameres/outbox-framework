package io.coffeeshop.core.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record MenuItem(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        Boolean isAvailable,
        UUID recipeId
) {
}
