package io.txbox.producer.starter.maintenance;

import io.txbox.producer.jpa.store.JpaOutboxStore;
import io.txbox.producer.starter.config.OutboxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Обслуживание outbox-таблицы по расписанию:
 * <ul>
 *   <li>purgeProcessed — удаление старых PROCESSED/FAILED/SKIPPED записей</li>
 *   <li>reclaimStale  — возврат зависших IN_FLIGHT обратно в PENDING</li>
 * </ul>
 *
 * <p>Запускается через {@link io.txbox.producer.starter.polling.OutboxSchedulerManager}.
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxMaintenance {

    private final JpaOutboxStore store;
    private final OutboxProperties properties;

    public void run() {
        purgeProcessed();
        reclaimStale();
    }

    private void purgeProcessed() {
        var retention = properties.maintenance().retention();
        int batchSize = properties.maintenance().batchSize();
        int deleted = store.purgeProcessed(retention, batchSize);
        if (deleted > 0) {
            log.info("OutboxMaintenance: purged {} processed messages older than {}", deleted, retention);
        }
    }

    private void reclaimStale() {
        var timeout = properties.polling().inFlightTimeout();
        int reclaimed = store.reclaimStale(timeout);
        if (reclaimed > 0) {
            log.warn("OutboxMaintenance: reclaimed {} stale IN_FLIGHT messages (timeout={})", reclaimed, timeout);
        }
    }
}
