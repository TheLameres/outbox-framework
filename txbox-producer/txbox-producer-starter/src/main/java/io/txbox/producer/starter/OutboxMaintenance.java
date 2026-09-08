package io.txbox.producer.starter;

import io.txbox.producer.jpa.store.JpaOutboxStore;
import io.txbox.producer.api.OutboxStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Обслуживание outbox-таблицы по расписанию:
 * <ul>
 *   <li>purgeProcessed — удаление старых PROCESSED/FAILED/SKIPPED записей</li>
 *   <li>reclaimStale  — возврат зависших IN_FLIGHT обратно в PENDING</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxMaintenance {

    private final JpaOutboxStore store;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString =
            "#{@outboxProperties.producer().maintenance().interval().toMillis()}")
    public void purge() {
        if (!properties.maintenance().enabled()) return;
        var retention = properties.maintenance().retention();
        var batchSize = properties.maintenance().batchSize();
        int deleted = store.purgeProcessed(retention, batchSize);
        if (deleted > 0) log.info("OutboxMaintenance: purged {} processed records", deleted);
    }

    @Scheduled(fixedDelayString =
            "#{@outboxProperties.producer().polling().inFlightTimeout().toMillis()}")
    public void reclaimStale() {
        var timeout = properties.polling().inFlightTimeout();
        store.reclaimStale(timeout);
    }
}
