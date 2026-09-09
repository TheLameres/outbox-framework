package io.txbox.consumer.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Маркер класса-слушателя входящих событий.
 * Все методы с @InboxEventHandler будут регистрироваться в InboxHandlerRegistry.
 *
 * <pre>{@code
 * @InboxEventListener(source = "Order")
 * @RequiredArgsConstructor
 * public class OrderInboxHandlers {
 *
 *     private final NotificationService notifications;
 *
 *     @InboxEventHandler(eventType = "OrderCreated")
 *     @Transactional
 *     public void onCreated(OrderEvent.Created event, InboundEventContext ctx) {
 *         notifications.notifyCreated(event.orderId(), event.total());
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface InboxEventListener {

    /**
     * Фильтр по aggregateType (txbox-aggregate-type header).
     * Пустая строка = принимать от всех source.
     */
    String source() default "";
}
