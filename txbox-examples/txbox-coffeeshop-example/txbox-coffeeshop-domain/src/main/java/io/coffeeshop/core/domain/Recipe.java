package io.coffeeshop.core.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Рецепт напитка: состав на одну порцию.
 *
 * <p>Инварианты: рецепт непустой, коды ингредиентов не повторяются.
 * Дублирование кода — почти всегда ошибка ввода, которая иначе превратилась бы
 * в двойное списание со склада.
 *
 * @param id          идентификатор рецепта
 * @param name        название, например {@code Flat White}
 * @param ingredients состав на одну порцию
 */
public record Recipe(UUID id, String name, List<IngredientRequirement> ingredients) {

    public Recipe {
        Objects.requireNonNull(id, "id");
        name = Validate.requireText(name, "name");
        Objects.requireNonNull(ingredients, "ingredients");
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("recipe must contain at least one ingredient");
        }
        ingredients = List.copyOf(ingredients);

        Set<String> seen = new HashSet<>();
        for (IngredientRequirement requirement : ingredients) {
            if (!seen.add(requirement.ingredientCode())) {
                throw new IllegalArgumentException(
                        "duplicate ingredient in recipe: " + requirement.ingredientCode());
            }
        }
    }

    /**
     * Состав на {@code portions} порций.
     */
    public Recipe scale(int portions) {
        if (portions <= 0) {
            throw new IllegalArgumentException("portions must be > 0, got " + portions);
        }
        return new Recipe(id, name,
                ingredients.stream().map(r -> r.times(portions)).toList());
    }

    /**
     * Полный список требований на {@code portions} порций — то, что уезжает
     * в событии {@code order.placed}.
     */
    public List<IngredientRequirement> requirementsFor(int portions) {
        return scale(portions).ingredients();
    }
}
