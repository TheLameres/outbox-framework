package io.coffeeshop.inventory.entity;

import io.coffeeshop.core.domain.Unit;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Резерв ингредиента под конкретный заказ.
 *
 * <p>Уникальный ключ {@code (order_id, ingredient_code)} — вторая линия защиты
 * от повторного резервирования. Первая — дедупликация inbox по messageId.
 * Дедупликация спасает от повторной доставки того же сообщения, но не от
 * повторной публикации с новым messageId; здесь спасает естественный ключ.
 */
@Entity
@Table(
        name = "reservations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reservation_order_ingredient",
                columnNames = {"order_id", "ingredient_code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationEntity {

    /** Статус резерва. */
    public enum Status {
        /** Ингредиент зарезервирован, ещё не списан. */
        RESERVED,
        /** Списание выполнено — ингредиент израсходован. */
        CONSUMED,
        /** Резерв снят, ингредиент вернулся в доступный остаток. */
        RELEASED
    }

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "ingredient_code", nullable = false, updatable = false, length = 64)
    private String ingredientCode;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Unit unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static ReservationEntity reserve(UUID orderId, String ingredientCode,
                                            BigDecimal amount, Unit unit) {
        var entity = new ReservationEntity();
        entity.id = UUID.randomUUID();
        entity.orderId = orderId;
        entity.ingredientCode = ingredientCode;
        entity.amount = amount;
        entity.unit = unit;
        entity.status = Status.RESERVED;
        entity.createdAt = Instant.now();
        return entity;
    }
}
