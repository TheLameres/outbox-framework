package com.company.outbox.core;

import java.time.Duration;
import java.util.UUID;

/**
 * Результат публикации. Sealed-иерархия вместо boolean/исключений:
 * компилятор гарантирует, что обработаны ВСЕ варианты, а добавление
 * нового варианта ломает сборку во всех местах разбора — это фича.
 */
public sealed interface PublishOutcome {

    UUID messageId();

    /** Успешно опубликовано. */
    record Published(UUID messageId, String destination, Duration latency)
            implements PublishOutcome {}

    /** Временная ошибка — брокер недоступен, таймаут. Имеет смысл повторить. */
    record Retryable(UUID messageId, String reason, Throwable cause)
            implements PublishOutcome {}

    /** Неустранимая ошибка — битый payload, несуществующий топик. Retry бесполезен. */
    record Fatal(UUID messageId, String reason, Throwable cause)
            implements PublishOutcome {}

    /** Сообщение отфильтровано политикой (например, отключённый тип события). */
    record Skipped(UUID messageId, String reason) implements PublishOutcome {}

    static PublishOutcome from(OutboxMessage message, Throwable error) {
        // instanceof-pattern + логика классификации ошибок в одном месте
        return switch (error) {
            case java.util.concurrent.TimeoutException t ->
                    new Retryable(message.id(), "broker timeout", t);
            case InterruptedException i ->
                    new Retryable(message.id(), "interrupted", i);
            case IllegalArgumentException e ->
                    new Fatal(message.id(), "invalid message: " + e.getMessage(), e);
            default ->
                    new Retryable(message.id(), error.getClass().getSimpleName(), error);
        };
    }
}
