package io.coffeeshop.core.domain;

import java.util.UUID;

public record Recipe(
        UUID id,
        IngredientRequirement ingredients
) {
}
