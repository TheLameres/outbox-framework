package io.coffeeshop.barista.listener;

import io.coffeeshop.barista.service.BaristaService;
import io.coffeeshop.core.event.InventoryEvents;
import io.coffeeshop.core.event.OrderEvents;
import io.txbox.consumer.annotation.InboxEventHandler;
import io.txbox.consumer.annotation.InboxEventListener;
import io.txbox.consumer.model.InboundEventContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Реакции бариста на события склада и заказов.
 *
 * <p>На методах намеренно нет {@code @Transactional}: транзакцию открывает
 * {@code InboxKafkaListener}. См. {@code InventoryOrderListener} — там же
 * объяснение, почему {@code @Transactional} на обработчике ломает регистрацию.
 */
@InboxEventListener(source = InventoryEvents.AGGREGATE_TYPE)
@Slf4j
@RequiredArgsConstructor
public class BaristaInventoryListener {

    private final BaristaService baristaService;

    @InboxEventHandler(eventType = "inventory.reserved")
    public void onIngredientsReserved(InventoryEvents.IngredientsReserved event,
                                      InboundEventContext context) {
        log.info("inventory.reserved: order={}, messageId={}",
                event.orderId(), context.messageId());
        baristaService.prepare(event);
    }
}
