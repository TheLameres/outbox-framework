package io.txbox.consumer.registrar;

import io.txbox.consumer.annotation.InboxEventHandler;
import io.txbox.consumer.annotation.InboxEventListener;
import io.txbox.consumer.model.InboundEventContext;
import io.txbox.consumer.starter.integration.TestPing;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Обработчик, падающий на первой попытке обработки каждого сообщения.
 *
 * <p>Вынесен в пакет {@code io.txbox.consumer.registrar} — отдельно от теста:
 * так проверяется, что {@code @InboxEventListener} находится сканированием
 * компонентов, а не подхватывается вместе с тестовым классом.
 *
 * <p>На первой попытке бросает {@link RuntimeException}. Классификатор
 * {@code ProcessingOutcomeClassifier} относит неизвестные исключения
 * к {@code Retryable} — именно этот путь и проверяется.
 *
 * <p>Счётчик ведётся по {@code messageId}: два теста в одном контексте
 * не должны влиять друг на друга.
 */
@Slf4j
@InboxEventListener(source = TestPing.AGGREGATE_TYPE)
public class FlakyPingListener {

    private final Map<UUID, AtomicInteger> attemptsByMessage = new ConcurrentHashMap<>();

    @InboxEventHandler(eventType = TestPing.EVENT_TYPE)
    public void onPing(TestPing event, InboundEventContext context) {
        int attempt = attemptsByMessage
                .computeIfAbsent(context.messageId(), id -> new AtomicInteger())
                .incrementAndGet();

        log.info("Ping attempt {}: messageId={}, text={}",
                attempt, context.messageId(), event.text());

        if (TestPing.FAIL_FATALLY.equals(event.text())) {
            // Постоянная ошибка: классификатор отнесёт её к Fatal,
            // повторной доставки быть не должно.
            throw new IllegalArgumentException("simulated permanent failure");
        }

        if (!TestPing.SUCCEED_IMMEDIATELY.equals(event.text()) && attempt == 1) {
            throw new RuntimeException("simulated transient failure, attempt " + attempt);
        }

        log.info("Ping processed: messageId={}, attempt={}", context.messageId(), attempt);
    }

    public int attempts(UUID messageId) {
        var counter = attemptsByMessage.get(messageId);
        return counter == null ? 0 : counter.get();
    }

    public void reset() {
        attemptsByMessage.clear();
    }
}
