package io.txbox.consumer.starter.health;

import io.txbox.consumer.store.InboxStore;
import io.txbox.core.store.MessageStats;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

/**
 * Health indicator для inbox.
 * Проверяет наличие зависших или упавших сообщений.
 */
@RequiredArgsConstructor
public class InboxHealthIndicator implements HealthIndicator {

    private final InboxStore store;

    @Override
    public Health health() {
        try {
            MessageStats stats = store.getStats();

            // Считаем UP если:
            // - нет FAILED сообщений, или их немного
            // - нет зависших RECEIVED дольше 5 минут

            if (stats.failed() > 10) {
                return Health.down()
                        .withDetail("failed_messages", stats.failed())
                        .build();
            }

            if (stats.oldestUnprocessed() != null) {
                long ageMs = System.currentTimeMillis() - stats.oldestUnprocessed().toEpochMilli();
                if (ageMs > 300_000) {  // 5 minutes
                    return Health.status(Status.OUT_OF_SERVICE)
                            .withDetail("oldest_received_age_ms", ageMs)
                            .withDetail("received_count", stats.received())
                            .build();
                }
            }

            return Health.up()
                    .withDetail("received", stats.received())
                    .withDetail("processed", stats.received())
                    .withDetail("failed", stats.received())
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withException(e)
                    .build();
        }
    }
}
