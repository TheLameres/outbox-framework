package io.txbox.core.event;

import java.lang.annotation.*;

/**
 * Маркер доменного события. Помечает классы и records, которые публикуются
 * в Apache Kafka через {@code OutboxTemplate}.
 *
 * <p>Заменяет прежний интерфейс {@code DomainEvent}: наследование от интерфейса
 * создавало жёсткую связанность и затрудняло тестирование. Теперь любой класс
 * или record становится событием одной аннотацией без ограничений на иерархию.
 *
 * <p>{@code eventType} по умолчанию — простое имя класса ({@code getSimpleName()}).
 * Явное значение используется при рефакторинге без миграции схемы или при
 * коллизии имён между разными пакетами.
 *
 * <pre>{@code
 * // простой случай — eventType = "OrderCreated"
 * @DomainEvent
 * public record OrderCreated(UUID id, BigDecimal sum, Instant occurredAt) {}
 *
 * // явный eventType — для schema evolution без переименования класса
 * @DomainEvent(eventType = "order.created.v2")
 * public record OrderCreatedV2(UUID id, BigDecimal sum, String currency, Instant occurredAt) {}
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DomainEvent {

    /**
     * Имя типа события для заголовка {@code txbox-event-type}.
     * Пустая строка означает «использовать {@code getClass().getSimpleName()}».
     */
    String eventType() default "";
}
