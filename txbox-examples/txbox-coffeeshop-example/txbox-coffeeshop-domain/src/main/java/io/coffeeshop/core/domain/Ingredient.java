package io.coffeeshop.core.domain;

/**
 * Ингредиент склада — справочные данные.
 *
 * <p>{@code code} — естественный ключ и единственная идентичность ингредиента,
 * известная другим сервисам. Суррогатный {@code UUID} здесь намеренно не используется:
 * он уникален только внутри одной базы, а ингредиент упоминается в рецептах и событиях
 * сразу нескольких сервисов.
 *
 * @param code      стабильный код, например {@code BEANS_ARABICA}
 * @param name      отображаемое имя
 * @param baseUnit  единица, в которой ведётся учёт на складе
 */
public record Ingredient(String code, String name, Unit baseUnit) {

    public Ingredient {
        code = Validate.requireText(code, "code");
        name = Validate.requireText(name, "name");
        java.util.Objects.requireNonNull(baseUnit, "baseUnit");
    }
}
