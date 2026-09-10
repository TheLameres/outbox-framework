package io.txbox.consumer.jpa.store;

import io.txbox.consumer.jpa.entity.InboxMessageEntity;
import io.txbox.consumer.jpa.repository.InboxJpaRepository;
import io.txbox.consumer.store.InboxStore;
import io.txbox.core.model.InboxMessage;
import io.txbox.core.model.MessageStatus;
import io.txbox.core.store.MessageStats;
import io.txbox.jpa.store.AbstractJpaMessageStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-реализация InboxStore.
 * Все методы вызываются В РАМКАХ ОДНОЙ транзакции обработки события —
 * это гарантирует atomicity: либо событие обработано И сохранено, либо оба откатываются.
 *
 * <p>Идемпотентность: save() проверяет (partition, offset) перед вставкой.
 * Уникальный индекс БД — вторая линия защиты от дублей при race condition.
 */
@Slf4j
public class JpaInboxStore
        extends AbstractJpaMessageStore<InboxMessage, InboxMessageEntity, InboxJpaRepository>
        implements InboxStore {

    public JpaInboxStore(InboxJpaRepository repository) {
        super(repository);
    }

    // ── save: идемпотентная вставка ───────────────────────────────────────────

    @Override
    @Transactional(transactionManager = "txBoxTransactionalManager")
    public void save(InboxMessage message) {
        boolean exists = repository
                .findByPartitionAndOffset(message.partition(), message.offset())
                .isPresent();
        if (exists) {
            log.debug("Inbox duplicate skipped: partition={}, offset={}",
                    message.partition(), message.offset());
            return;
        }
        repository.save(InboxMessageEntity.from(message));
    }

    // ── переходы статусов (в той же TX что и handler) ─────────────────────────

    @Override
    @Transactional(transactionManager = "txBoxTransactionalManager")
    public void markProcessed(UUID messageId) {
        repository.findById(messageId).ifPresentOrElse(
                e -> {
                    e.markProcessed();
                    log.debug("Inbox PROCESSED messageId={}", messageId);
                },
                () -> log.warn("markProcessed: messageId={} not found", messageId)
        );
    }

    @Override
    @Transactional(transactionManager = "txBoxTransactionalManager")
    public void markFailed(UUID messageId, String reason) {
        repository.findById(messageId).ifPresentOrElse(
                e -> {
                    e.markFailed(reason);
                    log.warn("Inbox FAILED messageId={}: {}", messageId, reason);
                },
                () -> log.warn("markFailed: messageId={} not found", messageId)
        );
    }

    @Override
    @Transactional(transactionManager = "txBoxTransactionalManager")
    public void markSkipped(UUID messageId, String reason) {
        repository.findById(messageId).ifPresentOrElse(
                e -> {
                    e.markSkipped(reason);
                    log.debug("Inbox SKIPPED messageId={}: {}", messageId, reason);
                },
                () -> log.warn("markSkipped: messageId={} not found", messageId)
        );
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "txBoxTransactionalManager")
    public Optional<InboxMessage> findByKafkaPosition(int partition, long offset) {
        return repository.findByPartitionAndOffset(partition, offset)
                .map(InboxMessageEntity::toMessage);
    }

    // ── Maintenance ───────────────────────────────────────────────────────────

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "txBoxTransactionalManager")
    protected int doPurgeProcessed(Instant before, int limit) {
        return repository.purgeProcessed(before, Limit.of(limit));
    }

    // ── Metrics / Health ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true, transactionManager = "txBoxTransactionalManager")
    public MessageStats getStats() {
        return new MessageStats(
                0L,                                              // pending — inbox не имеет
                0L,                                              // inFlight — inbox не имеет
                repository.countByStatus(MessageStatus.RECEIVED),
                repository.countByStatus(MessageStatus.PROCESSED),
                repository.countByStatus(MessageStatus.FAILED),
                repository.findOldestReceivedAt()
        );
    }

    // ── AbstractJpaMessageStore abstracts ─────────────────────────────────────

    @Override
    protected InboxMessage toMessage(InboxMessageEntity entity) {
        return entity.toMessage();
    }
}
