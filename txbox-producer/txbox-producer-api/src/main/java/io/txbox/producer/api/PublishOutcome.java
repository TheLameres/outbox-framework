package io.txbox.producer.api;

import java.time.Duration;
import java.util.UUID;

/**
 * Исход публикации одного сообщения. Sealed-иерархия вместо boolean/исключений:
 * компилятор гарантирует exhaustive switch, новый вариант ломает сборку везде.
 *
 * <p>OutboxPublisher никогда не бросает — все исходы здесь.
 */
public sealed interface PublishOutcome
        permits PublishOutcome.Published,
        PublishOutcome.Retryable,
        PublishOutcome.Fatal,
        PublishOutcome.Skipped {

    UUID messageId();

    /**
     * Успешно опубликовано.
     */
    record Published(UUID messageId, String destination, Duration latency)
            implements PublishOutcome {
    }

    /**
     * Временная ошибка — брокер недоступен, таймаут.
     * Поллер вернёт сообщение в PENDING, если attempt < maxRetries.
     */
    record Retryable(UUID messageId, String reason, Throwable cause)
            implements PublishOutcome {
    }

    /**
     * Неустранимая ошибка — RecordTooLarge, SerializationException.
     * Поллер переведёт в FAILED немедленно, не тратя попытки.
     */
    record Fatal(UUID messageId, String reason, Throwable cause)
            implements PublishOutcome {
    }

    /**
     * Сообщение пропущено намеренно (фильтр, дедупликация на стороне publisher).
     * Поллер переведёт в SKIPPED.
     */
    record Skipped(UUID messageId, String reason)
            implements PublishOutcome {
    }
}
