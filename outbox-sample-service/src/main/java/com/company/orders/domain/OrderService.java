package com.company.orders.domain;

import com.company.outbox.autoconfigure.OutboxTemplate;
import com.company.outbox.core.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxTemplate outbox;

    /** Бизнес-код видит один метод. Ни поллера, ни таблицы, ни Kafka. */
    @Transactional
    public UUID createOrder(UUID customerId, BigDecimal total) {
        var order = OrderEntity.create(customerId, total);
        orderRepository.save(order);

        outbox.publish(
                OrderEvent.Created.now(order.getId(), customerId, total),
                "Order",
                order.getId().toString());

        return order.getId();
    }

    /** Несколько событий одной транзакции — одна batch-вставка в outbox. */
    @Transactional
    public void completeOrder(UUID orderId, String paymentId, String tracking) {
        var order = orderRepository.findById(orderId).orElseThrow();
        order.markCompleted();

        List<OrderEvent> events = List.of(
                new OrderEvent.Paid(orderId, order.getTotal(), paymentId, java.time.Instant.now()),
                new OrderEvent.Shipped(orderId, tracking, java.time.Instant.now()));

        outbox.publishAll(events, "Order", orderId.toString());
    }

    /** Кастомная маршрутизация: конвертер как лямбда — интерфейс функциональный. */
    @Transactional
    public void cancelOrder(UUID orderId, String reason) {
        var order = orderRepository.findById(orderId).orElseThrow();
        order.markCancelled();

        var event = new OrderEvent.Cancelled(orderId, reason, order.isPaid(), java.time.Instant.now());

        outbox.publish(event, e -> OutboxMessage
                .of("Order", orderId.toString(), "OrderCancelled", serialize(e))
                .withHeader("destination", OrderEventRouting.topicFor(e))
                .withHeader("priority", e.refundRequired() ? "high" : "normal"));
    }

    private String serialize(OrderEvent event) {
        return "{}"; // в реальном коде — ObjectMapper
    }
}
