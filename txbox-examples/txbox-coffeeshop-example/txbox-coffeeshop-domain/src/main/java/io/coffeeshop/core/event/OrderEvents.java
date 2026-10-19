package io.coffeeshop.core.event;

import io.coffeeshop.core.domain.IngredientRequirement;
import io.coffeeshop.core.domain.Money;
import io.coffeeshop.core.domain.Unit;
import io.txbox.core.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * События контекста «Заказы».
 *
 * <p>Группировка через sealed-интерфейс: {@code eventType} задаётся явно, поэтому
 * переименование вложенного record не ломает контракт с потребителями.
 *
 * <pre>{@code
 * txbox-event-type: order.placed
 * txbox-aggregate-type: Order
 * txbox-aggregate-id: 8f14e45f-...
 * }</pre>
 *
 * <p>Все события неизменяемы и самодостаточны: потребитель не должен ходить
 * обратно в сервис заказа, чтобы понять содержимое.
 */
public sealed interface OrderEvents {

    /** Стабильное имя агрегата — попадает в заголовок {@code txbox-aggregate-type}. */
    String AGGREGATE_TYPE = "Order";

    // ── order.placed ──────────────────────────────────────────────────────────

    /**
     * Заказ оформлен. Публикуется в той же транзакции, что и вставка заказа.
     *
     * <p>Строка заказа несёт снимок рецепта: сервис склада резервирует ингредиенты
     * по данным события, не обращаясь в сервис меню. Это ключевое свойство —
     * событие является полным и не требует синхронного чтения при обработке.
     */
    @DomainEvent(eventType = "order.placed")
    record OrderPlaced(UUID orderId,
                       List<Line> lines,
                       Money total,
                       Instant placedAt) implements OrderEvents {

        public OrderPlaced {
            lines = List.copyOf(lines);
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("order must contain at least one line");
            }
        }

        /**
         * Строка заказа вместе с составом на одну порцию.
         *
         * @param requirements состав на одну порцию; суммарно нужно
         *                     {@code requirement.amount() * quantity}
         */
        public record Line(UUID menuItemId,
                           String name,
                           int quantity,
                           Money unitPrice,
                           List<IngredientRequirement> requirements) {

            public Line {
                name = name == null || name.isBlank() ? "unknown" : name;
                if (quantity <= 0) {
                    throw new IllegalArgumentException(
                            "quantity must be > 0, got " + quantity);
                }
                requirements = requirements == null ? List.of() : List.copyOf(requirements);
            }

            /**
             * Требования на всё количество в этой строке.
             */
            public List<IngredientRequirement> totalRequirements() {
                return requirements.stream().map(r -> r.times(quantity)).toList();
            }
        }
    }

    // ── order.cancelled ───────────────────────────────────────────────────────

    /**
     * Причина отмены или отказа.
     *
     * <p>Отдельный enum вместо свободного текста: потребитель принимает решения
     * по коду причины, а не разбирает строку.
     */
    enum CancellationReason {
        /** Отменил гость. */
        GUEST_REQUEST,
        /** Отменил оператор. */
        OPERATOR_REQUEST,
        /** Не хватило ингредиентов. */
        OUT_OF_STOCK,
        /** Не удалось приготовить. */
        PREPARATION_FAILED
    }

    /**
     * Заказ отменён. Для сервиса склада — команда освободить резерв.
     */
    @DomainEvent(eventType = "order.cancelled")
    record OrderCancelled(UUID orderId,
                          CancellationReason reason,
                          String details,
                          Instant cancelledAt) implements OrderEvents {
    }

    // ── order.completed ───────────────────────────────────────────────────────

    /**
     * Заказ выдан гостю — финальное событие саги.
     */
    @DomainEvent(eventType = "order.completed")
    record OrderCompleted(UUID orderId, Instant completedAt) implements OrderEvents {
    }
}
