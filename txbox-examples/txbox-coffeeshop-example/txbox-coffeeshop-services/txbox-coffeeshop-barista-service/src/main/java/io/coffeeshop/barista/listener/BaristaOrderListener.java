package io.coffeeshop.barista.listener;

import io.coffeeshop.barista.service.BaristaService;
import io.coffeeshop.core.event.OrderEvents;
import io.txbox.consumer.annotation.InboxEventHandler;
import io.txbox.consumer.annotation.InboxEventListener;
import io.txbox.consumer.model.InboundEventContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Реакции бариста на отмену заказа: тикет закрывается.
 *
 * <p>Отдельный слушатель по {@code source = "Order"} — демонстрация того,
 * что сервис может подписываться на разные агрегаты в одном топике.
 */
@InboxEventListener(source = OrderEvents.AGGREGATE_TYPE)
@Slf4j
@RequiredArgsConstructor
public class BaristaOrderListener {

    private final BaristaService baristaService;

    @InboxEventHandler(eventType = "order.cancelled")
    public void onOrderCancelled(OrderEvents.OrderCancelled event, InboundEventContext context) {
        log.info("order.cancelled: order={}, ticket closed", event.orderId());
        baristaService.cancelTicket(event.orderId(),
                event.reason() == null ? "CANCELLED" : event.reason().name());
    }
}
