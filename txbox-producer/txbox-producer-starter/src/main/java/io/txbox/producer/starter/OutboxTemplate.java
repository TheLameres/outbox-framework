package io.txbox.producer.starter;

import io.txbox.core.event.DomainEvent;
import io.txbox.core.model.OutboxMessage;
import io.txbox.producer.api.OutboxStore;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.function.Function;

/**
 * API для бизнес-кода. Сериализует DomainEvent → JSON → OutboxMessage → store.save().
 * Всегда вызывается внутри @Transactional-метода бизнес-операции (TX#1).
 *
 * <pre>{@code
 * @Transactional
 * public void createOrder(UUID customerId, BigDecimal total) {
 *     var order = orders.save(new Order(customerId, total));
 *     outbox.publish(
 *         new OrderEvent.Created(order.getId(), total, Instant.now()),
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
     * Публикует одно событие. eventType берётся из DomainEvent.eventType().
     */
    public void publish(DomainEvent event, String aggregateType, String aggregateId) {
        store.save(toMessage(event, aggregateType, aggregateId));
    }

    /**
     * Batch-публикация нескольких событий в одной TX.
     */
    public void publishAll(List<? extends DomainEvent> events,
                           String aggregateType, String aggregateId) {
        store.saveAll(events.stream()
                .map(e -> toMessage(e, aggregateType, aggregateId))
                .toList());
    }

    /**
     * Полный контроль через converter — кастомные заголовки, routing.
     */
    public void publish(DomainEvent event, Function<DomainEvent, OutboxMessage> converter) {
        store.save(converter.apply(event));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private OutboxMessage toMessage(DomainEvent event,
                                    String aggregateType, String aggregateId) {
        String payload = serialize(event);
        return OutboxMessage.of(aggregateType, aggregateId, event.eventType(), payload);
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize event: " + event, e);
        }
    }
}
