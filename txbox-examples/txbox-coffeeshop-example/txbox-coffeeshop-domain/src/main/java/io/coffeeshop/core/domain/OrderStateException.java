package io.coffeeshop.core.domain;

import java.util.UUID;

/**
 * Нарушение жизненного цикла заказа.
 *
 * <p>Наследуется от {@link IllegalStateException} осознанно: классификатор
 * {@code ProcessingOutcomeClassifier} в txbox-consumer-kafka трактует
 * {@code IllegalStateException} как {@code Fatal}. Нарушение бизнес-правила —
 * постоянная ошибка, повторять её бессмысленно, сообщение должно уйти в DLQ,
 * а не крутиться в бесконечном retry.
 */
public class OrderStateException extends IllegalStateException {

    private final UUID orderId;
    private final OrderStatus current;
    private final OrderStatus target;

    public OrderStateException(UUID orderId, OrderStatus current, OrderStatus target) {
        super("Order " + orderId + " cannot transition from " + current + " to " + target);
        this.orderId = orderId;
        this.current = current;
        this.target = target;
    }

    public UUID orderId() {
        return orderId;
    }

    public OrderStatus current() {
        return current;
    }

    public OrderStatus target() {
        return target;
    }
}
