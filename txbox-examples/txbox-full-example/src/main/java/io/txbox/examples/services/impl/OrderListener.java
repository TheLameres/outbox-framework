package io.txbox.examples.services.impl;

import io.txbox.consumer.annotation.InboxEventHandler;
import io.txbox.consumer.annotation.InboxEventListener;
import io.txbox.consumer.model.InboundEventContext;
import io.txbox.examples.dto.ChangeStatusDto;
import io.txbox.examples.dto.OrderDto;
import io.txbox.examples.dto.OrderStatus;
import io.txbox.examples.event.OrderEvents;
import io.txbox.examples.services.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@InboxEventListener(source = "Order")
@Slf4j
@RequiredArgsConstructor
public class OrderListener {

    private final OrderService orderService;

    @InboxEventHandler(eventType = "OrderCreated")
    public void orderCreatedListener(OrderEvents.OrderCreated event, InboundEventContext context) {
        log.info("Received from kafka: {}. Event: {}", context, event);
        var orderDto = orderService.changeOrderStatus(new ChangeStatusDto(event.id(), OrderStatus.PAID));
        log.info("Change Order Status: {}", orderDto);
    }

    @InboxEventHandler(eventType = "OrderChangeStatus")
    public void orderChangeStatus(OrderEvents.OrderChangeStatus event, InboundEventContext context) {
        log.info("Received from kafka: {}. Event: {}", context, event);
    }
}
