package io.coffeeshop.order.listener;

import io.coffeeshop.core.event.BaristaEvents;
import io.coffeeshop.order.service.OrderService;
import io.txbox.consumer.annotation.InboxEventHandler;
import io.txbox.consumer.annotation.InboxEventListener;
import io.txbox.consumer.model.InboundEventContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Реакции сервиса заказов на события бариста.
 */
@InboxEventListener(source = BaristaEvents.AGGREGATE_TYPE)
@Slf4j
@RequiredArgsConstructor
public class OrderBaristaListener {

    private final OrderService orderService;

    @InboxEventHandler(eventType = "barista.drink-prepared")
    public void onDrinkPrepared(BaristaEvents.DrinkPrepared event, InboundEventContext context) {
        log.info("barista.drink-prepared: order={}, ticket={}",
                event.orderId(), event.ticketNo());
        orderService.markReady(event.orderId());
    }

    @InboxEventHandler(eventType = "barista.drink-failed")
    public void onDrinkFailed(BaristaEvents.DrinkPreparationFailed event,
                              InboundEventContext context) {
        log.warn("barista.drink-failed: order={}, reason={}",
                event.orderId(), event.reason());
        orderService.failPreparation(event.orderId(), event.reason());
    }
}
