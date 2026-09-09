package io.txbox.consumer.starter.health;

import io.txbox.consumer.store.InboxStore;
import io.txbox.core.store.MessageStats;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator для inbox.
 * Проверяет наличие зависших или упавших сообщений.
 */
@Component("inboxHealth")
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

            if (stats.failedCount() > 10) {
                return Health.down()
                        .withDetail("failed_messages", stats.failedCount())
                        .build();
            }

            if (stats.oldestReceivedAt() != null) {
                long ageMs = System.currentTimeMillis() - stats.oldestReceivedAt().toEpochMilli();
                if (ageMs > 300_000) {  // 5 minutes
                    return Health.degraded()
                            .withDetail("oldest_received_age_ms", ageMs)
                            .withDetail("received_count", stats.receivedCount())
                            .build();
                }
            }

            return Health.up()
                    .withDetail("received", stats.receivedCount())
                    .withDetail("processed", stats.processedCount())
                    .withDetail("failed", stats.failedCount())
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withException(e)
                    .build();
        }
    }
}
