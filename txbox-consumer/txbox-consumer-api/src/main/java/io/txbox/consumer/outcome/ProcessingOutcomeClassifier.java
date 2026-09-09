package io.txbox.consumer.outcome;

import java.util.UUID;

/**
 * Классификатор для преобразования исключений в {@link ProcessingOutcome}.
 * Отделён от контракта, т.к. зависит от Spring, Kafka и JPA типов —
 * это логика application layer, а не API contract.
 *
 * <p>Используется в {@code InboxEventDispatcher} (Kafka consumer) для обработки исключений
 * выброшенных handler-методами.
 */
public final class ProcessingOutcomeClassifier {

    private ProcessingOutcomeClassifier() {
    }

    /**
     * Преобразует исключение в {@link ProcessingOutcome}.
     * Зеркально {@code PublishOutcome.classifyKafkaError} у producer.
     *
     * <p>Логика:
     * <ul>
     *   <li>Spring Data transient → {@link ProcessingOutcome.Retryable}</li>
     *   <li>Timeout → {@link ProcessingOutcome.Retryable}</li>
     *   <li>Business logic (validation, illegal state) → {@link ProcessingOutcome.Fatal}</li>
     *   <li>Unknown → {@link ProcessingOutcome.Retryable} (по умолчанию retry)</li>
     * </ul>
     */
    public static ProcessingOutcome classify(UUID messageId, Throwable t) {
        if (t == null) {
            return new ProcessingOutcome.Retryable(messageId, "unknown error", new Exception());
        }

        return switch (t) {
            // Spring Data/JPA transient errors
            case org.springframework.dao.TransientDataAccessException e ->
                    new ProcessingOutcome.Retryable(messageId, "transient DB error", e);
            case org.springframework.dao.DeadlockLoserDataAccessException e ->
                    new ProcessingOutcome.Retryable(messageId, "deadlock detected", e);

            // Timeout
            case java.util.concurrent.TimeoutException e ->
                    new ProcessingOutcome.Retryable(messageId, "processing timeout", e);

            // Validation / business logic — permanent failures
            case IllegalArgumentException e ->
                    new ProcessingOutcome.Fatal(messageId, "invalid: " + e.getMessage(), e);
            case IllegalStateException e ->
                    new ProcessingOutcome.Fatal(messageId, "illegal state: " + e.getMessage(), e);

            // Constraint violation (FK, unique, etc.) — usually permanent
            case org.springframework.dao.DataIntegrityViolationException e ->
                    new ProcessingOutcome.Fatal(messageId, "data integrity violation", e);

            // Default: retry
            default -> new ProcessingOutcome.Retryable(messageId, t.getClass().getSimpleName(), t);
        };
    }
}
