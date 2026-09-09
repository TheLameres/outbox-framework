package io.txbox.producer.starter;

import io.txbox.core.store.MessageStats;
import io.txbox.producer.jpa.store.JpaOutboxStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.time.Duration;
import java.time.Instant;

/**
 * Actuator health-check для outbox.
 * DOWN если oldest pending старше 30 секунд (брокер недоступен или поллер умер).
 */
@RequiredArgsConstructor
public class OutboxHealthIndicator implements HealthIndicator {

    private static final Duration UNHEALTHY_LAG = Duration.ofSeconds(30);

    private final JpaOutboxStore store;

    @Override
    public Health health() {
        MessageStats stats = store.getStats();
        var builder = Health.up()
                .withDetail("pending", stats.pending())
                .withDetail("inFlight", stats.inFlight())
                .withDetail("failed", stats.failed());

        if (stats.oldestUnprocessed() != null) {
            Duration lag = Duration.between(stats.oldestUnprocessed(), Instant.now());
            builder.withDetail("oldestPendingAge", lag.toString());
            if (lag.compareTo(UNHEALTHY_LAG) > 0) {
                return builder.down()
                        .withDetail("reason", "oldest pending message exceeds " + UNHEALTHY_LAG)
                        .build();
            }
        }
        return builder.build();
    }
}
