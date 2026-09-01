package com.company.outbox.core;

import java.util.Map;

/**
 * Стратегия маршрутизации сообщения в топик/очередь.
 * Реализации — records, что делает конфигурацию сравнимой и логируемой.
 */
public sealed interface DestinationResolver {

    String resolve(OutboxMessage message);

    /** {@code Order} -> {@code order-events} */
    record BySuffix(String suffix) implements DestinationResolver {
        public BySuffix {
            java.util.Objects.requireNonNull(suffix);
        }

        @Override
        public String resolve(OutboxMessage message) {
            return message.aggregateType().toLowerCase(java.util.Locale.ROOT) + suffix;
        }
    }

    /** Все события в один топик. */
    record Fixed(String destination) implements DestinationResolver {
        @Override
        public String resolve(OutboxMessage message) {
            return destination;
        }
    }

    /** Явная карта eventType -> destination с fallback. */
    record ByEventType(Map<String, String> mapping, String fallback)
            implements DestinationResolver {

        public ByEventType {
            mapping = Map.copyOf(mapping);
        }

        @Override
        public String resolve(OutboxMessage message) {
            return mapping.getOrDefault(message.eventType(), fallback);
        }
    }

    /** Первый resolver, вернувший непустое значение. */
    record Chain(java.util.List<DestinationResolver> delegates)
            implements DestinationResolver {

        public Chain {
            delegates = java.util.List.copyOf(delegates);
        }

        @Override
        public String resolve(OutboxMessage message) {
            return delegates.stream()
                    .map(d -> d.resolve(message))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No destination for " + message.eventType()));
        }
    }
}
