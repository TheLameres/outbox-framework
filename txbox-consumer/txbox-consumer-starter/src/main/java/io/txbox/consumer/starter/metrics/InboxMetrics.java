package io.txbox.consumer.starter.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.txbox.consumer.store.InboxStore;
import io.txbox.core.store.MessageStats;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Метрики для inbox.
 * Публикует в Micrometer счётчики RECEIVED, PROCESSED, FAILED.
 */
@Component
@RequiredArgsConstructor
public class InboxMetrics {

    private final InboxStore store;
    private final MeterRegistry registry;

    @Scheduled(fixedRateString = "${txbox.consumer.metrics.publish-interval:30000}")
    public void publishMetrics() {
        try {
            MessageStats stats = store.getStats();

            registry.gauge("txbox.inbox.received", stats.receivedCount());
            registry.gauge("txbox.inbox.processed", stats.processedCount());
            registry.gauge("txbox.inbox.failed", stats.failedCount());

        } catch (Exception e) {
            // Ignore — метрики не критичны
        }
    }
}
