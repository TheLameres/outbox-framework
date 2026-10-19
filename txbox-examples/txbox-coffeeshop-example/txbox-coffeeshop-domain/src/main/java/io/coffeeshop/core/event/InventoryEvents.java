package io.coffeeshop.core.event;

import io.coffeeshop.core.domain.Unit;
import io.txbox.core.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * События контекста «Склад».
 */
public sealed interface InventoryEvents {

    /** Стабильное имя агрегата — заголовок {@code txbox-aggregate-type}. */
    String AGGREGATE_TYPE = "Inventory";

    /**
     * Ингредиенты зарезервированы. Сервис заказа переводит заказ
     * в {@code CONFIRMED}, сервис бариста начинает готовить.
     */
    @DomainEvent(eventType = "inventory.reserved")
    record IngredientsReserved(UUID orderId,
                               List<Item> items,
                               Instant reservedAt) implements InventoryEvents {

        public IngredientsReserved {
            items = List.copyOf(items);
        }

        /**
         * Зарезервированное количество.
         */
        public record Item(String ingredientCode,
                           BigDecimal amount,
                           Unit unit) {

            public Item {
                if (ingredientCode == null || ingredientCode.isBlank()) {
                    throw new IllegalArgumentException("ingredientCode must not be blank");
                }
                if (amount == null || amount.signum() <= 0) {
                    throw new IllegalArgumentException("amount must be > 0");
                }
                if (unit == null) {
                    throw new IllegalArgumentException("unit must not be null");
                }
            }
        }
    }

    /**
     * Резерв невозможен: хотя бы одного ингредиента не хватает.
     *
     * <p>Несёт {@code missing} — какие именно позиции не сошлись. Это позволяет
     * сервису заказа показать гостю осмысленный отказ и не гадать о причине.
     * Резерв не создаётся частично: сага либо подтверждает заказ целиком, либо нет.
     */
    @DomainEvent(eventType = "inventory.rejected")
    record IngredientsRejected(UUID orderId,
                               List<Missing> missing,
                               Instant rejectedAt) implements InventoryEvents {

        public IngredientsRejected {
            missing = List.copyOf(missing);
        }

        /**
         * Чего не хватило.
         */
        public record Missing(String ingredientCode,
                              BigDecimal requested,
                              BigDecimal available,
                              Unit unit) {
        }
    }

    /**
     * Резерв снят — компенсация после отмены заказа.
     */
    @DomainEvent(eventType = "inventory.released")
    record IngredientsReleased(UUID orderId, Instant releasedAt) implements InventoryEvents {
    }
}
