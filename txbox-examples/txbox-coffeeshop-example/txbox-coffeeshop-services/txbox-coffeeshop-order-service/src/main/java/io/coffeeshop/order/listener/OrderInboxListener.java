package io.coffeeshop.order.listener;

import io.coffeeshop.core.event.BaristaEvents;
import io.coffeeshop.core.event.InventoryEvents;
import io.coffeeshop.order.service.OrderService;
import io.txbox.consumer.annotation.InboxEventHandler;
import io.txbox.consumer.annotation.InboxEventListener;
import io.txbox.consumer.model.InboundEventContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Реакции сервиса заказов на события склада и бариста.
 *
 * <h2>Почему на методах нет {@code @Transactional}</h2>
 *
 * <p>{@code InboxKafkaListener.listen()} уже открывает транзакцию и вызывает
 * диспетчер внутри неё. Добавление {@code @Transactional} на метод-обработчик
 * сделало бы бин проксированным, а {@code InboxEventDispatcher} ищет аннотации
 * на методах фактического класса бина через {@code getDeclaredMethods()}.
 * У CGLIB-прокси аннотации методов не копируются, поэтому обработчик
 * перестал бы регистрироваться — молча, с исходом «no handlers registered».
 *
 * <p>Транзакции это не вредит: внешняя транзакция listener'а покрывает и вызов
 * обработчика, и запись в inbox, и запись в outbox.
 */
@InboxEventListener(source = InventoryEvents.AGGREGATE_TYPE)
@Slf4j
@RequiredArgsConstructor
public class OrderInboxListener {

    private final OrderService orderService;

    @InboxEventHandler(eventType = "inventory.reserved")
    public void onIngredientsReserved(InventoryEvents.IngredientsReserved event,
                                      InboundEventContext context) {
        log.info("inventory.reserved: order={}, messageId={}", event.orderId(), context.messageId());
        orderService.confirm(event.orderId());
    }

    @InboxEventHandler(eventType = "inventory.rejected")
    public void onIngredientsRejected(InventoryEvents.IngredientsRejected event,
                                      InboundEventContext context) {
        String details = event.missing().stream()
                .map(m -> m.ingredientCode() + " (requested " + m.requested().toPlainString()
                        + ", available " + m.available().toPlainString() + ")")
                .reduce((a, b) -> a + "; " + b)
                .orElse("unknown");
        log.info("inventory.rejected: order={}, missing={}", event.orderId(), details);
        orderService.reject(event.orderId(), "out of stock: " + details);
    }

    /**
     * Резерв освобождён. Статус заказа не меняется: отмена или отказ уже
     * перевели его в терминальное состояние, это событие — подтверждение
     * компенсации, а не команда.
     */
    @InboxEventHandler(eventType = "inventory.released")
    public void onIngredientsReleased(InventoryEvents.IngredientsReleased event,
                                      InboundEventContext context) {
        log.info("inventory.released: order={} compensated", event.orderId());
    }
}
