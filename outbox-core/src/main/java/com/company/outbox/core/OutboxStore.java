package com.company.outbox.core;

import java.util.List;
import java.util.UUID;

/** Контракт хранилища. Реализации: JPA, JDBC, R2DBC. */
public interface OutboxStore {

    /** Сохраняет сообщение в ТЕКУЩЕЙ транзакции бизнес-операции. */
    void save(OutboxMessage message);

    /** Батч-вставка — при нескольких событиях на одну транзакцию. */
    default void saveAll(List<OutboxMessage> messages) {
        messages.forEach(this::save);
    }

    /**
     * Забирает пачку PENDING с {@code FOR UPDATE SKIP LOCKED}
     * и переводит их в {@link OutboxStatus#IN_FLIGHT}.
     * Возвращает {@code SequencedCollection} — порядок по created_at важен.
     */
    List<OutboxMessage> claimBatch(int batchSize);

    /** Применяет результаты публикации одной транзакцией. */
    void applyOutcomes(List<PublishOutcome> outcomes, int maxRetries);

    /** Очистка обработанных записей старше retention. */
    int purgeProcessed(java.time.Duration retention, int limit);

    /** Для DLQ-эндпоинта: ручной повтор FAILED-сообщения. */
    void requeue(UUID messageId);
}
