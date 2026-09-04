package com.company.orders.domain;

/** Маршрутизация на топики через pattern matching по sealed-иерархии. */
public final class OrderEventRouting {

    private OrderEventRouting() {
    }

    /**
     * Ни default, ни else — компилятор сам проверяет полноту.
     * Добавили новый record в OrderEvent -> сборка падает здесь. Это и нужно.
     */
    public static String topicFor(OrderEvent event) {
        return switch (event) {
            case OrderEvent.Created ignored -> "orders.created";
            case OrderEvent.Paid p when p.amount().signum() == 0 -> "orders.zero-payment.audit";
            case OrderEvent.Paid ignored -> "orders.paid";
            case OrderEvent.Shipped ignored -> "orders.shipped";
            case OrderEvent.Cancelled c when c.refundRequired() -> "orders.refunds";
            case OrderEvent.Cancelled ignored -> "orders.cancelled";
        };
    }

    /** Record pattern: деструктуризация прямо в case-метке. */
    public static String describe(OrderEvent event) {
        return switch (event) {
            case OrderEvent.Created(var id, var customer, var total, var at) ->
                    "Заказ %s создан клиентом %s на сумму %s (%s)".formatted(id, customer, total, at);
            case OrderEvent.Paid(var id, var amount, var paymentId, var at) ->
                    "Заказ %s оплачен на %s, платёж %s (%s)".formatted(id, amount, paymentId, at);
            case OrderEvent.Shipped(var id, var tracking, var at) ->
                    "Заказ %s отправлен, трек %s (%s)".formatted(id, tracking, at);
            case OrderEvent.Cancelled(var id, var reason, var refund, var at) ->
                    "Заказ %s отменён: %s%s".formatted(id, reason, refund ? ", нужен возврат" : "");
        };
    }
}
