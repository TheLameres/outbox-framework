package io.txbox.producer.api;

import io.txbox.core.model.OutboxMessage;

import java.util.List;
import java.util.UUID;

/**
 * Контракт хранилища outbox (producer-сторона).
 * Реализации: JPA (txbox-producer-jpa), JDBC, R2DBC.
 *
 * <p>Три транзакции:
 * <ol>
 *   <li>Бизнес-логика + {@link #save} — одна TX с бизнес-данными</li>
 *   <li>{@link #claimBatch} — отдельная TX, FOR UPDATE SKIP LOCKED</li>
 *   <li>{@link #applyOutcomes} — отдельная TX после публикации вне TX</li>
 * </ol>
 */
public interface OutboxStore {

    /** Сохраняет сообщение в ТЕКУЩЕЙ транзакции бизнес-операции. */
    void save(OutboxMessage message);

    /** Batch-вставка — несколько событий в одной TX. */
    default void saveAll(List<OutboxMessage> messages) {
        messages.forEach(this::save);
    }

    /**
     * Захватывает пачку PENDING-записей (FOR UPDATE SKIP LOCKED),
     * переводит их в IN_FLIGHT и возвращает в порядке created_at.
     */
    List<OutboxMessage> claimBatch(int batchSize);

    /**
     * Применяет результаты публикации одной транзакцией:
     * Published → PROCESSED, Retryable → PENDING (если attempt < maxRetries) или FAILED,
     * Fatal → FAILED, Skipped → SKIPPED.
     */
    void applyOutcomes(List<PublishOutcome> outcomes, int maxRetries);

    /** Очищает PROCESSED/FAILED/SKIPPED старше retention. */
    int purgeProcessed(java.time.Duration retention, int limit);

    /** Возвращает IN_FLIGHT записи без обновлений дольше timeout обратно в PENDING. */
    int reclaimStale(java.time.Duration timeout);

    /** Ручной повтор FAILED-сообщения (для DLQ-эндпоинта). */
    void requeue(UUID messageId);
}
