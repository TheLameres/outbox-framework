package io.txbox.consumer.kafka.util;

import io.txbox.consumer.outcome.ProcessingOutcome;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.TransientDataAccessException;

import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Классификатор исключений → {@link ProcessingOutcome}.
 *
 * <p>Намеренно размещён в {@code txbox-consumer-kafka}, а не в
 * {@code txbox-consumer-api}: зависит от Spring Data и Kafka-специфичных типов,
 * которых нет в api-слое. Используется в {@link io.txbox.consumer.kafka.dispatcher.InboxEventDispatcher}.
 *
 * <p>Зеркало {@code KafkaOutboxPublisher.classifyKafkaError} на producer-стороне.
 */
public final class ProcessingOutcomeClassifier {

    private ProcessingOutcomeClassifier() {}

    public static ProcessingOutcome classify(UUID messageId, Throwable t) {
        if (t == null) {
            return new ProcessingOutcome.Retryable(messageId, "unknown error", new RuntimeException());
        }

        return switch (t) {
            // Spring Data/JPA transient — retry
            case DeadlockLoserDataAccessException e ->
                    new ProcessingOutcome.Retryable(messageId, "deadlock detected", e);

            // Timeout — retry
            case TransientDataAccessException e ->
                    new ProcessingOutcome.Retryable(messageId, "transient DB error", e);
            case TimeoutException e ->
                    new ProcessingOutcome.Retryable(messageId, "processing timeout", e);

            // Constraint violation — permanent
            case DataIntegrityViolationException e ->
                    new ProcessingOutcome.Fatal(messageId, "data integrity violation", e);

            // Business logic — permanent
            case IllegalArgumentException e ->
                    new ProcessingOutcome.Fatal(messageId, "invalid argument: " + e.getMessage(), e);
            case IllegalStateException e ->
                    new ProcessingOutcome.Fatal(messageId, "illegal state: " + e.getMessage(), e);

            // Default — retry
            default -> new ProcessingOutcome.Retryable(messageId, t.getClass().getSimpleName(), t);
        };
    }
}
