package io.txbox.core.store;

import io.txbox.core.model.Message;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Generic контракт хранилища для любого типа сообщения.
 * OutboxStore и InboxStore наследуют от него, добавляя специфичные методы.
 *
 * @param <T> конкретный тип сообщения: OutboxMessage или InboxMessage
 */
public interface MessageStore<T extends Message> {

    /** Сохраняет сообщение (insert). */
    void save(T message);

    /** Возвращает true, если сообщение уже завершилось (PROCESSED / FAILED / SKIPPED). */
    boolean wasProcessed(UUID messageId);

    /** Находит сообщение по ID. */
    Optional<T> findById(UUID messageId);

    /** Агрегированная статистика по очереди. */
    MessageStats getStats();

    /**
     * Очищает завершённые записи старше {@code retention}.
     *
     * @param retention возраст записей для удаления
     * @param limit     максимальное число удаляемых записей за один вызов
     * @return фактическое число удалённых записей
     */
    int purgeProcessed(Duration retention, int limit);
}
