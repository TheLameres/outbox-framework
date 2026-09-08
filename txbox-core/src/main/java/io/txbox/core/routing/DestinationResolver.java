package io.txbox.core.routing;

import io.txbox.core.model.OutboxMessage;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Стратегия маршрутизации исходящего сообщения в топик/очередь.
 * Sealed-иерархия: исчерпывающий switch, новый вариант ломает компиляцию.
 */
public sealed interface DestinationResolver
        permits DestinationResolver.BySuffix,
                DestinationResolver.Fixed,
                DestinationResolver.ByEventType,
                DestinationResolver.Chain {

    String resolve(OutboxMessage message);

    /** {@code "Order"} → {@code "order-events"} */
    record BySuffix(String suffix) implements DestinationResolver {
        public BySuffix {
            Objects.requireNonNull(suffix, "suffix");
        }

        @Override
        public String resolve(OutboxMessage message) {
            return message.aggregateType().toLowerCase(java.util.Locale.ROOT) + suffix;
        }
    }

    /** Все события — в один фиксированный топик. */
    record Fixed(String destination) implements DestinationResolver {
        public Fixed {
            Objects.requireNonNull(destination, "destination");
        }

        @Override
        public String resolve(OutboxMessage message) {
            return destination;
        }
    }

    /** Явная карта eventType → destination с fallback-значением. */
    record ByEventType(Map<String, String> mapping, String fallback)
            implements DestinationResolver {

        public ByEventType {
            mapping  = Map.copyOf(Objects.requireNonNull(mapping, "mapping"));
            Objects.requireNonNull(fallback, "fallback");
        }

        @Override
        public String resolve(OutboxMessage message) {
            return mapping.getOrDefault(message.eventType(), fallback);
        }
    }

    /** Первый resolver, вернувший непустое значение, выигрывает. */
    record Chain(List<DestinationResolver> delegates) implements DestinationResolver {
        public Chain {
            delegates = List.copyOf(Objects.requireNonNull(delegates, "delegates"));
        }

        @Override
        public String resolve(OutboxMessage message) {
            return delegates.stream()
                    .map(d -> d.resolve(message))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No destination resolved for eventType=" + message.eventType()));
        }
    }
}
