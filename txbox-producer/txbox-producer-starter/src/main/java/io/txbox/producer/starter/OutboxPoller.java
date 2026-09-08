package io.txbox.producer.starter;

import io.txbox.core.model.OutboxMessage;
import io.txbox.core.routing.DestinationResolver;
import io.txbox.producer.api.OutboxPublisher;
import io.txbox.producer.api.OutboxStore;
import io.txbox.producer.api.PublishOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Поллер outbox: claim → publish (вне TX) → applyOutcomes.
 * Три отдельные транзакции — намеренно.
 *
 * <p>Публикация батча параллельна на виртуальных потоках (Java 21+),
 * ограничена семафором concurrency для защиты брокера от перегрузки.
 */
@Slf4j
public class OutboxPoller {

    private final OutboxStore store;
    private final OutboxPublisher publisher;
    private final OutboxProperties properties;
    private final ExecutorService executor;

    public OutboxPoller(OutboxStore store,
                        OutboxPublisher publisher,
                        OutboxProperties properties) {
        this.store      = store;
        this.publisher  = publisher;
        this.properties = properties;
        this.executor   = properties.producer().polling().virtualThreads()
                ? Executors.newVirtualThreadPerTaskExecutor()
                : Executors.newFixedThreadPool(properties.producer().polling().concurrency());
    }

    @Scheduled(fixedDelayString = "#{@outboxProperties.producer().polling().interval().toMillis()}")
    public void poll() {
        if (!properties.producer().polling().enabled()) return;

        // TX#2 — захват пачки
        List<OutboxMessage> batch = store.claimBatch(
                properties.producer().polling().batchSize());
        if (batch.isEmpty()) return;

        log.debug("OutboxPoller: claimed {} messages", batch.size());

        // вне TX — параллельная публикация
        List<PublishOutcome> outcomes = publishConcurrently(batch);

        // TX#3 — применить результаты
        store.applyOutcomes(outcomes, properties.producer().retry().maxAttempts());
    }

    private List<PublishOutcome> publishConcurrently(List<OutboxMessage> batch) {
        var semaphore = new java.util.concurrent.Semaphore(
                properties.producer().polling().concurrency());
        return batch.stream()
                .map(msg -> executor.submit(() -> {
                    semaphore.acquire();
                    try {
                        return publisher.publish(msg);
                    } finally {
                        semaphore.release();
                    }
                }))
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        log.error("Unexpected error publishing message", e);
                        return (PublishOutcome) new PublishOutcome.Retryable(
                                null, "unexpected: " + e.getMessage(), e);
                    }
                })
                .toList();
    }
}
