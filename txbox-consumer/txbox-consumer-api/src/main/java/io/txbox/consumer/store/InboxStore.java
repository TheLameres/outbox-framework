package io.txbox.consumer.store;

import io.txbox.core.model.InboxMessage;
import io.txbox.core.store.MessageStore;

import java.util.Optional;
import java.util.UUID;

/**
 * Контракт хранилища inbox (consumer-сторона).
 * Расширяет {@link MessageStore} с методами для жизненного цикла сообщения:
 * save (RECEIVED) → markProcessed (PROCESSED) / markFailed / markSkipped.
 *
 * <p>Все методы вызываются В ОДНОЙ ТРАНЗАКЦИИ обработки события — это гарантирует
 * atomicity: либо событие обработано И сохранено в БД, либо обе операции откатываются.
 */
public interface InboxStore extends MessageStore<InboxMessage> {

    /**
     * Сохраняет сообщение как RECEIVED.
     * Вызывается в начале TX обработки события из Kafka.
     */
    @Override
    void save(InboxMessage message);

    /**
     * Помечает успешно обработанное сообщение.
     * Вызывается в конце TX обработки (если handler вернул Success).
     */
    void markProcessed(UUID messageId);

    /**
     * Помечает как FAILED с описанием ошибки.
     * Вызывается если handler выбросил Fatal exception.
     */
    void markFailed(UUID messageId, String reason);

    /**
     * Помечает как SKIPPED (дубликат или фильтр).
     * Вызывается если дедупликация обнаружила duplicate по (partition, offset).
     */
    void markSkipped(UUID messageId, String reason);

    /**
     * Найти сообщение по (partition, offset) — для дедупликации.
     * Вызывается до save(), чтобы проверить, не видели ли это раньше.
     */
    Optional<InboxMessage> findByKafkaPosition(int partition, long offset);
}
