package io.txbox.examples.services.impl;

import io.txbox.consumer.annotation.InboxEventHandler;
import io.txbox.consumer.annotation.InboxEventListener;
import io.txbox.consumer.model.InboundEventContext;
import io.txbox.examples.event.OrderEvents;
import lombok.extern.slf4j.Slf4j;

@InboxEventListener(source = "Order")
@Slf4j
public class OrderListener {

    @InboxEventHandler(eventType = "OrderCreated")
    public void orderCreatedListener(OrderEvents.OrderCreated event, InboundEventContext context) {
        log.info("Received from kafka: {}. Event: {}", context, event);
    }
}
