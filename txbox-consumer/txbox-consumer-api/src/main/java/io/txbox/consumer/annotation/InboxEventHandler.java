package io.txbox.consumer.annotation;

import java.lang.annotation.*;

/**
 * Маркер метода-обработчика входящего события.
 * Параметры:
 * <ul>
 *   <li>Десериализованное событие (DomainEvent или POJO) — маршируется по типу</li>
 *   <li>{@link io.txbox.consumer.model.InboundEventContext} (опционально) — метаданные</li>
 * </ul>
 *
 * <p>Метод должен быть {@code @Transactional} — обработка идёт в одной TX,
 * где также сохраняется статус в inbox-таблице.
 *
 * <p>Исключение → {@link io.txbox.consumer.outcome.ProcessingOutcome.Retryable}
 * (если не маркирована как {@code Fatal} через {@code fromThrowable}).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InboxEventHandler {

    /**
     * Тип события. Сопоставляется с txbox-event-type header.
     * Wildcard {@code "*"} — принимает любой eventType.
     */
    String eventType();

    /**
     * Проверять идемпотентность через InboxStore.wasProcessed().
     * Если {@code true} и messageId уже обработан — пропустить обработку,
     * вернуть {@link io.txbox.consumer.outcome.ProcessingOutcome.Skipped}.
     */
    boolean idempotent() default true;

    /**
     * Порядок выполнения среди handlers того же eventType.
     * Меньшее значение = раньше. Используется @Order у Spring.
     */
    int order() default 0;
}
