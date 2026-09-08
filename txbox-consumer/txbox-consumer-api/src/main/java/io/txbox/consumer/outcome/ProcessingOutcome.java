package io.txbox.consumer.outcome;

import java.util.UUID;

/**
 * Исход обработки входящего события. Sealed-иерархия вместо boolean/исключений.
 *
 * <p>Success → commit offset, сохранить PROCESSED в inbox.
 * <p>Retryable → NACK offset, оставить RECEIVED для retry позже.
 * <p>Fatal → commit offset, отправить в DLQ, сохранить FAILED в inbox.
 * <p>Skipped → commit offset, сохранить SKIPPED в inbox (дубликат или фильтр).
 */
public sealed interface ProcessingOutcome permits
        ProcessingOutcome.Success,
        ProcessingOutcome.Retryable,
        ProcessingOutcome.Fatal,
        ProcessingOutcome.Skipped {

    UUID messageId();

    /**
     * Успешно обработано.
     * → commit offset, PROCESSED в inbox
     */
    record Success(UUID messageId) implements ProcessingOutcome {}

    /**
     * Временная ошибка (DB transient, timeout, вспомогательный сервис недоступен).
     * → NACK, retry позже
     */
    record Retryable(UUID messageId, String reason, Throwable cause)
            implements ProcessingOutcome {}

    /**
     * Постоянная ошибка (invalid payload, business logic violated).
     * → commit offset, отправить в DLQ, FAILED в inbox (чтобы не потерять)
     */
    record Fatal(UUID messageId, String reason, Throwable cause)
            implements ProcessingOutcome {}

    /**
     * Сообщение пропущено намеренно (дубликат через wasProcessed, фильтр).
     * → commit offset, SKIPPED в inbox
     */
    record Skipped(UUID messageId, String reason)
            implements ProcessingOutcome {}

    /**
     * Классификация исключения: что-то типичное → Retryable/Fatal.
     * Зеркально {@code PublishOutcome.classifyKafkaError} у producer.
     */
    static ProcessingOutcome fromThrowable(UUID messageId, Throwable t) {
        if (t == null) return new Retryable(messageId, "unknown error", new Exception());
        
        return switch (t) {
            // Spring Data/JPA transient errors
            case org.springframework.dao.TransientDataAccessException e ->
                    new Retryable(messageId, "transient DB error", e);
            // Timeout
            case java.util.concurrent.TimeoutException e ->
                    new Retryable(messageId, "processing timeout", e);
            // Validation / business logic
            case IllegalArgumentException e ->
                    new Fatal(messageId, "invalid: " + e.getMessage(), e);
            case IllegalStateException e ->
                    new Fatal(messageId, "illegal state: " + e.getMessage(), e);
            // Default: retry
            default -> new Retryable(messageId, t.getClass().getSimpleName(), t);
        };
    }
}
