package io.coffeeshop.inventory.entity;

import io.coffeeshop.core.domain.StockLevel;
import io.coffeeshop.core.domain.Unit;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Остаток ингредиента.
 *
 * <p>Пара {@code onHand} / {@code reserved} повторяет доменную модель: списание
 * и резервирование — разные операции, и без разделения отмена заказа не смогла бы
 * вернуть ингредиент, который уже был списан.
 */
@Entity
@Table(name = "stock_levels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockLevelEntity {

    @Id
    @Column(name = "ingredient_code", length = 64)
    private String ingredientCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Unit unit;

    @Column(name = "on_hand", nullable = false, precision = 14, scale = 2)
    private BigDecimal onHand;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal reserved;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static StockLevelEntity from(StockLevel level) {
        var entity = new StockLevelEntity();
        entity.ingredientCode = level.ingredientCode();
        entity.unit = level.unit();
        entity.onHand = level.onHand();
        entity.reserved = level.reserved();
        entity.updatedAt = Instant.now();
        return entity;
    }

    public void apply(StockLevel level) {
        this.onHand = level.onHand();
        this.reserved = level.reserved();
        this.updatedAt = Instant.now();
    }

    public StockLevel toDomain() {
        return new StockLevel(ingredientCode, unit, onHand, reserved);
    }
}
