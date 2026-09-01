package com.company.outbox.autoconfigure;

import com.company.outbox.jpa.JpaOutboxStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/** Фоновые задачи обслуживания: возврат зависших и очистка обработанных. */
@Slf4j
@RequiredArgsConstructor
public class OutboxMaintenance {

    private final JpaOutboxStore store;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${outbox.polling.in-flight-timeout:5m}")
    public void reclaimStale() {
        store.reclaimStale(properties.polling().inFlightTimeout());
    }

    @Scheduled(fixedDelayString = "${outbox.cleanup.interval:1h}")
    public void purge() {
        if (!properties.cleanup().enabled()) {
            return;
        }
        int purged = store.purgeProcessed(
                properties.cleanup().retention(), properties.cleanup().batchSize());
        if (purged > 0) {
            log.info("outbox: purged {} processed messages", purged);
        }
    }
}
