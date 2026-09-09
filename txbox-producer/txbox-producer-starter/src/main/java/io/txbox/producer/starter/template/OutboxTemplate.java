package io.txbox.producer.starter.template;

import io.txbox.core.event.DomainEvent;
import io.txbox.core.model.OutboxMessage;
import io.txbox.producer.api.OutboxStore;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.function.Function;

/**
 * Публичный API для бизнес-кода. Сериализует объект, помеченный {@link DomainEvent},
 * в JSON → OutboxMessage → store.save().
 *
 * <p>Принимает любой объект с аннотацией {@code @DomainEvent}: class, record,
 * sealed-иерархия — без наследования от интерфейса.
 * eventType берётся из {@code @DomainEvent.eventType()}, либо из
 * {@code getClass().getSimpleName()}, если атрибут не задан.
 *
 * <p>Всегда вызывается внутри {@code @Transactional}-метода (TX#1).
 *
 * <pre>{@code
 * @DomainEvent
 * public record OrderCreated(UUID id, BigDecimal sum, Instant occurredAt) {}
 *
 * @Transactional
 * public void createOrder(UUID customerId, BigDecimal total) {
 *     var order = orders.save(new Order(customerId, total));
 *     outbox.publish(
 *         new OrderCreated(order.getId(), total, Instant.now()),
 *         "Order",
 *         order.getId().toString()
 *     );
 * }
 * }</pre>
 */
@RequiredArgsConstructor
public class OutboxTemplate {

    private final OutboxStore store;
    private final ObjectMapper objectMapper;

    /**
     * Публикует одно событие. Объект должен быть помечен {@code @DomainEvent}.
     * eventType берётся из аннотации или getSimpleName().
     */
    public void publish(Object event, String aggregateType, String aggregateId) {
        store.save(toMessage(event, aggregateType, aggregateId));
    }

    /**
     * Batch-публикация нескольких событий в одной TX.
     * Все объекты должны быть помечены {@code @DomainEvent}.
     */
    public void publishAll(List<?> events, String aggregateType, String aggregateId) {
        store.saveAll(events.stream()
                .map(e -> toMessage(e, aggregateType, aggregateId))
                .toList());
    }

    /**
     * Полный контроль через converter — кастомные заголовки, routing.
     */
    public void publish(Object event, Function<Object, OutboxMessage> converter) {
        store.save(converter.apply(event));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private OutboxMessage toMessage(Object event, String aggregateType, String aggregateId) {
        String eventType = resolveEventType(event);
        String payload = serialize(event);
        return OutboxMessage.of(aggregateType, aggregateId, eventType, payload);
    }

    /**
     * Определяет eventType из аннотации {@code @DomainEvent}.
     * Если атрибут eventType не задан (пустая строка) — берёт getSimpleName().
     *
     * @throws IllegalArgumentException если класс не помечен {@code @DomainEvent}
     */
    private String resolveEventType(Object event) {
        Class<?> clazz = event.getClass();
        DomainEvent annotation = clazz.getAnnotation(DomainEvent.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    "Event class " + clazz.getName() + " is not annotated with @DomainEvent. " +
                    "Add @DomainEvent to the event class or record.");
        }
        String explicit = annotation.eventType();
        return explicit.isBlank() ? clazz.getSimpleName() : explicit;
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize event: " + event, e);
        }
    }
}
