package io.coffeeshop.inventory.entity;

import io.coffeeshop.core.domain.Ingredient;
import io.coffeeshop.core.domain.Unit;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Справочник ингредиентов.
 *
 * <p>Первичный ключ — естественный {@code code}, а не суррогатный UUID:
 * код ингредиента приходит из событий и рецептов других сервисов, и только он
 * может быть ключом в распределённой системе.
 */
@Entity
@Table(name = "ingredients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngredientEntity {

    @Id
    @Column(length = 64)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_unit", nullable = false, length = 20)
    private Unit baseUnit;

    public static IngredientEntity of(String code, String name, Unit baseUnit) {
        var entity = new IngredientEntity();
        entity.code = code;
        entity.name = name;
        entity.baseUnit = baseUnit;
        return entity;
    }

    public Ingredient toDomain() {
        return new Ingredient(code, name, baseUnit);
    }
}
