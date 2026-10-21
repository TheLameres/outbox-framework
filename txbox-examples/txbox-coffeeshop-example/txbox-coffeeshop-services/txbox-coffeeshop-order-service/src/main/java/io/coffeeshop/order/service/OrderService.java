package io.coffeeshop.order.service;

import io.coffeeshop.core.domain.Order;
import io.coffeeshop.core.domain.OrderLine;
import io.coffeeshop.core.domain.OrderStateException;
import io.coffeeshop.core.domain.OrderStatus;
import io.coffeeshop.core.event.OrderEvents;
import io.coffeeshop.order.entity.MenuItemEntity;
import io.coffeeshop.order.entity.OrderEntity;
import io.coffeeshop.order.repository.MenuItemRepository;
import io.coffeeshop.order.repository.OrderRepository;
import io.coffeeshop.order.web.dto.OrderResponse;
import io.coffeeshop.order.web.dto.PlaceOrderRequest;
import io.txbox.producer.starter.template.OutboxTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Прикладной сервис заказов.
 *
 * <p>Каждый метод, меняющий состояние, — одна транзакция: бизнес-запись и запись
 * в outbox коммитятся вместе. Это и есть суть паттерна — событие не может
 * «уехать» без изменения состояния и наоборот.
 *
 * <p>Правила переходов статуса живут в домене ({@link Order}), а не здесь:
 * сервис только загружает, вызывает доменный метод и сохраняет.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final MenuItemRepository menuItemRepository;
    private final OutboxTemplate outboxTemplate;

    /**
     * Оформляет заказ и публикует {@code order.placed} в той же транзакции.
     */
    @Transactional
    public OrderResponse place(PlaceOrderRequest request) {
        List<OrderLine> lines = new ArrayList<>();
        // Строки события собираем в том же проходе, что и строки заказа:
        // второй проход по запросу потребовал бы повторного чтения меню
        // и сопоставления по индексу, которое ломается при любой правке.
        List<OrderEvents.OrderPlaced.Line> eventLines = new ArrayList<>();

        for (PlaceOrderRequest.Line requested : request.lines()) {
            MenuItemEntity menuItem = menuItemRepository.findByCode(requested.menuItemCode())
                    .orElseThrow(() -> NotFoundException.menuItem(requested.menuItemCode()));

            lines.add(new OrderLine(menuItem.getId(), menuItem.getName(),
                    requested.quantity(), menuItem.price()));

            eventLines.add(new OrderEvents.OrderPlaced.Line(
                    menuItem.getId(),
                    menuItem.getName(),
                    requested.quantity(),
                    menuItem.price(),
                    menuItem.requirements()));
        }

        Order order = Order.place(UUID.randomUUID(), lines, Instant.now());
        orderRepository.save(OrderEntity.from(order));

        // TX#1: заказ и событие коммитятся одной транзакцией.
        // Доставит событие poller — уже вне этой транзакции.
        outboxTemplate.publish(
                new OrderEvents.OrderPlaced(order.id(), eventLines, order.total(), order.placedAt()),
                OrderEvents.AGGREGATE_TYPE,
                order.id().toString());

        log.info("Order placed: id={}, total={}", order.id(), order.total());
        return OrderResponse.from(order);
    }

    /**
     * Отменяет заказ по инициативе гостя или оператора.
     *
     * <p>{@code order.cancelled} — команда сервису склада освободить резерв.
     */
    @Transactional
    public OrderResponse cancel(UUID orderId, String reason) {
        return applyAndPublishCancellation(
                orderId,
                order -> order.cancel(reason),
                OrderEvents.CancellationReason.GUEST_REQUEST,
                reason);
    }

    /**
     * Приготовить не удалось: заказ отменяется и публикуется {@code order.cancelled},
     * чтобы склад освободил зарезервированные ингредиенты.
     */
    @Transactional
    public void failPreparation(UUID orderId, String reason) {
        applyAndPublishCancellation(
                orderId,
                order -> order.cancel("preparation failed: " + reason),
                OrderEvents.CancellationReason.PREPARATION_FAILED,
                reason);
    }

    // ── переходы, инициированные событиями ────────────────────────────────────

    /**
     * Склад зарезервировал ингредиенты: {@code PLACED -> CONFIRMED}.
     */
    @Transactional
    public void confirm(UUID orderId) {
        transition(orderId, "confirm", Order::confirm);
    }

    /**
     * Склад не смог зарезервировать: {@code PLACED -> REJECTED}.
     *
     * <p>Компенсация не публикуется: склад резерв не создавал, освобождать нечего.
     */
    @Transactional
    public void reject(UUID orderId, String reason) {
        transition(orderId, "reject", order -> order.reject(reason));
    }

    /**
     * Напиток готов: {@code PREPARING|CONFIRMED -> READY}.
     */
    @Transactional
    public void markReady(UUID orderId) {
        // Событие бариста приходит, когда заказ уже CONFIRMED, а домен требует
        // пройти через PREPARING. Шагаем по графу переходов, а не перепрыгиваем
        // его: иначе правило жизненного цикла пришлось бы ослабить ради удобства.
        transition(orderId, "markReady", order -> order.status() == OrderStatus.CONFIRMED
                ? order.startPreparing().markReady()
                : order.markReady());
    }

    // ── чтение ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderResponse findById(UUID orderId) {
        return OrderResponse.from(load(orderId).toDomain());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        return orderRepository.findAll().stream()
                .map(OrderEntity::toDomain)
                .map(OrderResponse::from)
                .toList();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void transition(UUID orderId, String action, UnaryOperator<Order> mover) {
        OrderEntity entity = load(orderId);
        Order moved;
        try {
            moved = mover.apply(entity.toDomain());
        } catch (OrderStateException e) {
            // Повторная доставка уже применённого события — штатная ситуация
            // при at-least-once. Логируем и выходим, чтобы не ронять обработку:
            // исключение здесь дало бы вечный retry.
            log.warn("Order {} transition '{}' ignored: {}", orderId, action, e.getMessage());
            return;
        }
        entity.apply(moved);
        orderRepository.save(entity);
        log.info("Order {}: {} -> {}", orderId, action, moved.status());
    }

    private OrderResponse applyAndPublishCancellation(
            UUID orderId,
            UnaryOperator<Order> mover,
            OrderEvents.CancellationReason reasonCode,
            String details) {

        OrderEntity entity = load(orderId);
        Order cancelled = mover.apply(entity.toDomain());
        entity.apply(cancelled);
        orderRepository.save(entity);

        outboxTemplate.publish(
                new OrderEvents.OrderCancelled(orderId, reasonCode, details, Instant.now()),
                OrderEvents.AGGREGATE_TYPE,
                orderId.toString());

        log.info("Order cancelled: id={}, reason={}", orderId, reasonCode);
        return OrderResponse.from(cancelled);
    }

    private OrderEntity load(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> NotFoundException.order(orderId));
    }
}
