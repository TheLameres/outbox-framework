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
     * Пропускать handler, если событие с тем же {@code messageId} уже завершено в inbox
     * (PROCESSED / FAILED / SKIPPED) и пришло повторно под другим (partition, offset).
     * Проверка выполняется через {@code InboxStore.wasProcessed(messageId)}.
     *
     * <p>Дубликат по тому же (partition, offset) отсекается раньше, на уровне listener,
     * независимо от этого флага. {@code false} — handler выполнится и для дубликата
     * (нужно, если побочный эффект сам идемпотентен или должен повторяться).
     */
    boolean idempotent() default true;

    /**
     * Порядок выполнения среди handlers того же eventType.
     * Меньшее значение = раньше. Используется @Order у Spring.
     */
    int order() default 0;
}
