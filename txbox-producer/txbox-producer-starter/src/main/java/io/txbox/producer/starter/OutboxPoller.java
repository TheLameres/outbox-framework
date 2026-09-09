package io.txbox.producer.starter;

import io.txbox.core.model.OutboxMessage;
import io.txbox.producer.api.OutboxPublisher;
import io.txbox.producer.api.OutboxStore;
import io.txbox.producer.api.PublishOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Поллер outbox: claim → publish (вне TX) → applyOutcomes.
 * Три отдельные транзакции — намеренно.
 *
 * <p>Публикация батча параллельна на виртуальных потоках (Java 21+),
 * ограничена семафором concurrency для защиты брокера от перегрузки.
 *
 * <p>ExecutorService создаётся один раз в конструкторе и живёт
 * вместе с бином — не пересоздаётся на каждый poll().
 */
@Slf4j
public class OutboxPoller {

    private final OutboxStore store;
    private final OutboxPublisher publisher;
    private final OutboxProperties properties;
    private final ExecutorService executor;
    private final Semaphore semaphore;

    public OutboxPoller(OutboxStore store,
                        OutboxPublisher publisher,
                        OutboxProperties properties) {
        this.store = store;
        this.publisher = publisher;
        this.properties = properties;

        int concurrency = properties.polling().concurrency();
        this.semaphore = new Semaphore(concurrency);
        this.executor = properties.polling().virtualThreads()
                ? Executors.newVirtualThreadPerTaskExecutor()
                : Executors.newFixedThreadPool(concurrency);
    }

    public void poll() {
        if (!properties.enabled() || !properties.polling().enabled()) return;

        // TX#2 — захват пачки
        List<OutboxMessage> batch = store.claimBatch(properties.polling().batchSize());
        if (batch.isEmpty()) return;

        log.debug("OutboxPoller: claimed {} messages", batch.size());

        // вне TX — параллельная публикация
        List<PublishOutcome> outcomes = publishConcurrently(batch);

        // TX#3 — применить результаты
        store.applyOutcomes(outcomes, properties.retry().maxAttempts());
    }

    private List<PublishOutcome> publishConcurrently(List<OutboxMessage> batch) {
        // Шаг 1: submit все задачи, сохраняем Future в том же порядке что и batch
        List<Future<PublishOutcome>> futures = new ArrayList<>(batch.size());
        for (OutboxMessage msg : batch) {
            futures.add(executor.submit(() -> {
                semaphore.acquire();
                try {
                    return publisher.publish(msg);
                } finally {
                    semaphore.release();
                }
            }));
        }

        // Шаг 2: собираем результаты, сохраняя соответствие messageId → outcome
        List<PublishOutcome> outcomes = new ArrayList<>(batch.size());
        for (int i = 0; i < futures.size(); i++) {
            OutboxMessage msg = batch.get(i);
            try {
                outcomes.add(futures.get(i).get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while waiting for publish result, messageId={}",
                        msg.messageId());
                outcomes.add(new PublishOutcome.Retryable(
                        msg.messageId(), "interrupted", e));
            } catch (Exception e) {
                log.error("Unexpected error collecting publish result, messageId={}",
                        msg.messageId(), e);
                outcomes.add(new PublishOutcome.Retryable(
                        msg.messageId(), "unexpected: " + e.getMessage(), e));
            }
        }
        return outcomes;
    }
}
