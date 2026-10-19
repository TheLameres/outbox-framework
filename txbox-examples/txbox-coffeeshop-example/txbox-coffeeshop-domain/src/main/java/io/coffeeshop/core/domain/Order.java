package io.coffeeshop.core.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Агрегат заказа — неизменяемый.
 *
 * <p>Переходы статуса возвращают новый экземпляр, а не мутируют поля.
 * Это делает правила жизненного цикла проверяемыми в обычном unit-тесте,
 * без Spring, БД и Kafka.
 *
 * <p>Все переходы проходят через {@link #transitionTo(OrderStatus, String)}:
 * единственная точка, где проверяется допустимость перехода, поэтому
 * нелегальный переход невозможно «протащить» в обход правил.
 *
 * @param id            идентификатор заказа
 * @param lines         состав заказа, минимум одна строка
 * @param status        текущий статус
 * @param placedAt      момент оформления
 * @param failureReason причина отказа или отмены, если есть
 */
public record Order(UUID id,
                    List<OrderLine> lines,
                    OrderStatus status,
                    Instant placedAt,
                    String failureReason) {

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("order must contain at least one line");
        }
        lines = List.copyOf(lines);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(placedAt, "placedAt");
    }

    /**
     * Новый заказ в статусе {@link OrderStatus#PLACED}.
     */
    public static Order place(UUID id, List<OrderLine> lines, Instant placedAt) {
        return new Order(id, lines, OrderStatus.PLACED, placedAt, null);
    }

    /**
     * Сумма заказа.
     */
    public Money total() {
        return lines.stream()
                .map(OrderLine::total)
                .reduce(Money::plus)
                .orElseThrow(() -> new IllegalStateException(
                        "order " + id + " has no lines: total is undefined"));
    }

    // ── переходы ──────────────────────────────────────────────────────────────

    public Order confirm() {
        return transitionTo(OrderStatus.CONFIRMED, null);
    }

    public Order startPreparing() {
        return transitionTo(OrderStatus.PREPARING, null);
    }

    public Order markReady() {
        return transitionTo(OrderStatus.READY, null);
    }

    public Order complete() {
        return transitionTo(OrderStatus.COMPLETED, null);
    }

    /**
     * Отмена инициатором (гостем или оператором).
     */
    public Order cancel(String reason) {
        return transitionTo(OrderStatus.CANCELLED, reason);
    }

    /**
     * Отказ по бизнес-причине: склад не смог зарезервировать ингредиенты.
     */
    public Order reject(String reason) {
        return transitionTo(OrderStatus.REJECTED, reason);
    }

    /**
     * Единственная точка смены статуса.
     *
     * @throws OrderStateException если переход недопустим по {@link OrderStatus#canTransitionTo}
     */
    private Order transitionTo(OrderStatus target, String reason) {
        if (!status.canTransitionTo(target)) {
            throw new OrderStateException(id, status, target);
        }
        String failure = this.failureReason;
        if (target == OrderStatus.CANCELLED || target == OrderStatus.REJECTED) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "reason is required when transitioning to " + target);
            }
            failure = reason;
        }
        return new Order(id, lines, target, placedAt, failure);
    }
}
