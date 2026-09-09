package io.txbox.producer.starter.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.txbox.producer.jpa.store.JpaOutboxStore;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;

/**
 * Micrometer-метрики outbox (producer).
 *
 * <ul>
 *   <li>{@code txbox.outbox.lag.seconds}  — возраст старейшего PENDING (gauge)</li>
 *   <li>{@code txbox.outbox.pending}       — число PENDING (gauge)</li>
 *   <li>{@code txbox.outbox.in_flight}     — число IN_FLIGHT (gauge)</li>
 *   <li>{@code txbox.outbox.failed}        — число FAILED (gauge)</li>
 * </ul>
 *
 * <p>Counter-метрики (publish outcome) регистрируются в OutboxPoller через
 * MeterRegistry.counter("txbox.publish.outcome", "result", "published|retried|fatal|skipped").
 */
@Slf4j
public class OutboxMetrics {

    public OutboxMetrics(JpaOutboxStore store, MeterRegistry registry) {
        Gauge.builder("txbox.outbox.lag.seconds", store, s -> {
                    Instant oldest = s.getStats().oldestUnprocessed();
                    if (oldest == null) return 0d;
                    return Duration.between(oldest, Instant.now()).toMillis() / 1000.0;
                })
                .description("Age in seconds of the oldest PENDING outbox message")
                .register(registry);

        Gauge.builder("txbox.outbox.pending", store, s -> s.getStats().pending())
                .description("Number of PENDING outbox messages")
                .register(registry);

        Gauge.builder("txbox.outbox.in_flight", store, s -> s.getStats().inFlight())
                .description("Number of IN_FLIGHT outbox messages")
                .register(registry);

        Gauge.builder("txbox.outbox.failed", store, s -> s.getStats().failed())
                .description("Number of permanently FAILED outbox messages")
                .register(registry);
    }
}
