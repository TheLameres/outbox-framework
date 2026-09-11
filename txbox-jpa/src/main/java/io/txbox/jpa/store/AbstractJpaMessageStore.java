package io.txbox.jpa.store;

import io.txbox.core.model.Message;
import io.txbox.core.store.MessageStats;
import io.txbox.core.store.MessageStore;
import io.txbox.jpa.entity.AbstractMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Generic JPA base для OutboxStore и InboxStore.
 * Конкретные наследники реализуют только специфичные методы;
 * общие — wasProcessed, findById, purgeProcessed — живут здесь.
 *
 * @param <T> тип доменного сообщения (OutboxMessage / InboxMessage)
 * @param <E> тип JPA-сущности (OutboxEventEntity / InboxMessageEntity)
 * @param <R> Spring Data репозиторий
 */
public abstract class AbstractJpaMessageStore<
        T extends Message,
        E extends AbstractMessageEntity,
        R extends JpaRepository<E, UUID>>
        implements MessageStore<T> {

    protected final R repository;

    protected AbstractJpaMessageStore(R repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "txBoxTransactionalManager")
    public boolean wasProcessed(UUID messageId) {
        return repository.findById(messageId)
                .map(e -> e.getStatus().isTerminal())
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "txBoxTransactionalManager")
    public Optional<T> findById(UUID messageId) {
        return repository.findById(messageId).map(this::toMessage);
    }

    @Override
    @Transactional(transactionManager = "txBoxTransactionalManager")
    public int purgeProcessed(Duration retention, int limit) {
        return doPurgeProcessed(Instant.now().minus(retention), limit);
    }

    // ── абстрактные методы для конкретных наследников ──────────────────────

    /**
     * Конвертирует JPA-сущность в доменный объект.
     */
    protected abstract T toMessage(E entity);

    /**
     * Удаляет записи в статусах PROCESSED/FAILED/SKIPPED
     * с processedAt старше {@code before}.
     *
     * @return число удалённых записей
     */
    protected abstract int doPurgeProcessed(Instant before, int limit);

    /**
     * Конкретные наследники должны реализовать getStats() с запросом
     * к своей таблице — общая реализация невозможна без знания имени таблицы.
     */
    @Override
    public abstract MessageStats getStats();
}
