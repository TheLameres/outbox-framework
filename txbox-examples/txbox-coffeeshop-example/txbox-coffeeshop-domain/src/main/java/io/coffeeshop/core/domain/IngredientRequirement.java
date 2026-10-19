package io.coffeeshop.core.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Строка рецепта: сколько ингредиента нужно на одну порцию.
 *
 * <p>Единица измерения хранится рядом с количеством, а не берётся из карточки
 * ингредиента: рецепт должен быть самодостаточным, иначе его нельзя передать
 * в событии другому сервису.
 *
 * @param ingredientCode код ингредиента ({@link Ingredient#code()})
 * @param amount         количество на одну порцию, строго больше нуля
 * @param unit           единица измерения
 */
public record IngredientRequirement(String ingredientCode, BigDecimal amount, Unit unit) {

    public IngredientRequirement {
        ingredientCode = Validate.requireText(ingredientCode, "ingredientCode");
        amount = Validate.requirePositive(amount, "amount");
        Objects.requireNonNull(unit, "unit");
    }

    /**
     * Требование на несколько порций.
     */
    public IngredientRequirement times(int portions) {
        if (portions <= 0) {
            throw new IllegalArgumentException("portions must be > 0, got " + portions);
        }
        return new IngredientRequirement(
                ingredientCode,
                amount.multiply(BigDecimal.valueOf(portions)),
                unit);
    }
}
