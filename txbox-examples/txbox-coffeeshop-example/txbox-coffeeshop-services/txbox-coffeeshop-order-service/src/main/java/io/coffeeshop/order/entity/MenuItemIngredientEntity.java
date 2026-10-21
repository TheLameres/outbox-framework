package io.coffeeshop.order.entity;

import io.coffeeshop.core.domain.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Строка рецепта позиции меню: сколько ингредиента нужно на одну порцию.
 */
@Entity
@Table(name = "menu_item_ingredients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuItemIngredientEntity {

    @Id
    private UUID id;

    @Column(name = "ingredient_code", nullable = false, length = 64)
    private String ingredientCode;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Unit unit;
}
