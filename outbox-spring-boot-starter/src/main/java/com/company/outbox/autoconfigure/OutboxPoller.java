package com.company.outbox.autoconfigure;

import com.company.outbox.core.OutboxMessage;
import com.company.outbox.core.OutboxPublisher;
import com.company.outbox.core.OutboxStore;
import com.company.outbox.core.PublishOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Поллер: claim -> публикация -> применение результатов.
 *
 * <p><b>Три транзакции, а не одна.</b> Публикация в брокер вынесена ЗА пределы
 * транзакции: держать соединение с БД открытым во время сетевого I/O — верный
 * способ выесть пул под нагрузкой. Статус IN_FLIGHT + {@code reclaimStale}
 * закрывают дыру на случай падения инстанса между шагами.
 *
 * <p><b>Виртуальные потоки (Java 21).</b> Публикация батча идёт параллельно на
 * virtual threads: блокирующий {@code future.get()} внутри Kafka-продюсера не
 * занимает платформенный поток. Транзакционный контекст в виртуальные потоки НЕ
 * пробрасывается — и не должен: на этом шаге БД не используется.
 */
@Slf4j
public class OutboxPoller {

    private final OutboxStore store;
    private final OutboxPublisher publisher;
    private final OutboxProperties properties;
    private final MeterRegistry meters;

    /**
     * Gauge должен ссылаться на живой объект: meters.gauge(name, value) с новым
     * Double на каждом вызове создаёт метрику, которую тут же собирает GC.
     */
    private final AtomicLong lagSeconds = new AtomicLong();

    public OutboxPoller(OutboxStore store,
                        OutboxPublisher publisher,
                        OutboxProperties properties,
                        MeterRegistry meters) {
        this.store = store;
        this.publisher = publisher;
        this.properties = properties;
        this.meters = meters;
        io.micrometer.core.instrument.Gauge
                .builder("outbox.lag.seconds", lagSeconds, AtomicLong::doubleValue)
                .description("Возраст самого старого необработанного сообщения")
                .register(meters);
    }

    /** Локальный record — группировка результатов без отдельного файла. */
    private record BatchResult(int published, int retried, int fatal) {
        static BatchResult of(List<PublishOutcome> outcomes) {
            int published = 0;
            int retried = 0;
            int fatal = 0;
            for (PublishOutcome outcome : outcomes) {
                // Pattern matching for switch по sealed-иерархии
                switch (outcome) {
                    case PublishOutcome.Published ignored -> published++;
                    case PublishOutcome.Skipped ignored -> published++;
                    case PublishOutcome.Retryable ignored -> retried++;
                    case PublishOutcome.Fatal ignored -> fatal++;
                }
            }
            return new BatchResult(published, retried, fatal);
        }

        boolean isEmpty() {
            return published + retried + fatal == 0;
        }
    }

    // SpEL-обращение к record-у @ConfigurationProperties по имени бина НЕ работает:
    // бин регистрируется как "outbox-com.company...OutboxProperties". Используем placeholder —
    // fixedDelayString понимает Duration-строки ("500ms", "1s", "PT1S").
    @Scheduled(fixedDelayString = "${outbox.polling.interval:1s}")
    public void poll() {
        // 1) TX #1 — claim с SKIP LOCKED, перевод в IN_FLIGHT
        List<OutboxMessage> batch = store.claimBatch(properties.polling().batchSize());
        if (batch.isEmpty()) {
            return;
        }

        // SequencedCollection (Java 21): getFirst() без batch.get(0)
        recordLag(batch.getFirst());

        // 2) Вне транзакции — публикация
        List<PublishOutcome> outcomes = properties.polling().virtualThreads()
                ? publishConcurrently(batch)
                : batch.stream().map(this::publishOne).toList();

        // 3) TX #2 — применение результатов
        store.applyOutcomes(outcomes, properties.retry().maxAttempts());

        var result = BatchResult.of(outcomes);
        if (!result.isEmpty()) {
            log.debug("outbox: batch={} published={} retried={} fatal={}",
                    batch.size(), result.published(), result.retried(), result.fatal());
        }
    }

    /** Виртуальные потоки: один поток на сообщение, никакого пула настраивать не нужно. */
    private List<PublishOutcome> publishConcurrently(List<OutboxMessage> batch) {
        var limiter = new Semaphore(properties.polling().concurrency());

        // ExecutorService реализует AutoCloseable (Java 19+): close() ждёт завершения задач
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<PublishOutcome>> tasks = batch.stream()
                    .map(message -> (Callable<PublishOutcome>) () -> {
                        limiter.acquire();
                        try {
                            return publishOne(message);
                        } finally {
                            limiter.release();
                        }
                    })
                    .toList();

            return executor.invokeAll(tasks).stream()
                    .map(OutboxPoller::resultOf)
                    .filter(java.util.Objects::nonNull)
                    .toList();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("outbox: polling interrupted, batch returns to PENDING via reclaimStale");
            return List.of();
        }
    }

    private PublishOutcome publishOne(OutboxMessage message) {
        Timer.Sample sample = Timer.start(meters);
        try {
            PublishOutcome outcome = publisher.publish(message);
            countOutcome(message, outcome);
            return outcome;
        } catch (RuntimeException e) {
            // Publisher не должен бросать, но контракт защищаем
            return PublishOutcome.from(message, e);
        } finally {
            sample.stop(meters.timer("outbox.publish",
                    "event_type", message.eventType()));
        }
    }

    private void countOutcome(OutboxMessage message, PublishOutcome outcome) {
        String status = switch (outcome) {
            case PublishOutcome.Published ignored -> "published";
            case PublishOutcome.Skipped ignored -> "skipped";
            case PublishOutcome.Retryable ignored -> "retryable";
            case PublishOutcome.Fatal ignored -> "fatal";
        };
        meters.counter("outbox.outcomes",
                "status", status,
                "event_type", message.eventType(),
                "aggregate_type", message.aggregateType()).increment();
    }

    private void recordLag(OutboxMessage oldest) {
        Duration lag = Duration.between(oldest.createdAt(), Instant.now());
        lagSeconds.set(lag.toSeconds());
    }

    private static PublishOutcome resultOf(Future<PublishOutcome> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (java.util.concurrent.ExecutionException e) {
            log.error("outbox: publisher task failed", e.getCause());
            return null;
        }
    }
}
