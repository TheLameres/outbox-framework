package io.coffeeshop.inventory.listener;

import io.coffeeshop.core.event.OrderEvents;
import io.coffeeshop.inventory.service.InventoryService;
import io.txbox.consumer.annotation.InboxEventHandler;
import io.txbox.consumer.annotation.InboxEventListener;
import io.txbox.consumer.model.InboundEventContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Реакции склада на события заказов.
 *
 * <p>На методах намеренно нет {@code @Transactional}: транзакцию открывает
 * {@code InboxKafkaListener}, а {@code @Transactional} на обработчике превратил бы
 * бин в CGLIB-прокси, у которого диспетчер не найдёт аннотаций методов.
 */
@InboxEventListener(source = OrderEvents.AGGREGATE_TYPE)
@Slf4j
@RequiredArgsConstructor
public class InventoryOrderListener {

    private final InventoryService inventoryService;

    @InboxEventHandler(eventType = "order.placed")
    public void onOrderPlaced(OrderEvents.OrderPlaced event, InboundEventContext context) {
        log.info("order.placed: order={}, messageId={}", event.orderId(), context.messageId());
        inventoryService.reserve(event);
    }

    @InboxEventHandler(eventType = "order.cancelled")
    public void onOrderCancelled(OrderEvents.OrderCancelled event, InboundEventContext context) {
        log.info("order.cancelled: order={}, reason={}", event.orderId(), event.reason());
        inventoryService.release(event);
    }
}
