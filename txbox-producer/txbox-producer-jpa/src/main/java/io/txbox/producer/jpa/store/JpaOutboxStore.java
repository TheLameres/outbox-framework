package io.txbox.producer.jpa.store;

import io.txbox.core.model.MessageStatus;
import io.txbox.core.model.OutboxMessage;
import io.txbox.core.store.MessageStats;
import io.txbox.jpa.store.AbstractJpaMessageStore;
import io.txbox.producer.api.OutboxStore;
import io.txbox.producer.api.PublishOutcome;
import io.txbox.producer.jpa.entity.OutboxEventEntity;
import io.txbox.producer.jpa.repository.OutboxJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * JPA-реализация OutboxStore.
 * Наследует wasProcessed, findById, purgeProcessed из AbstractJpaMessageStore.
 * Добавляет producer-специфичные методы: claimBatch, applyOutcomes, reclaimStale, requeue.
 */
@Slf4j
public class JpaOutboxStore
        extends AbstractJpaMessageStore<OutboxMessage, OutboxEventEntity, OutboxJpaRepository>
        implements OutboxStore {

    public JpaOutboxStore(OutboxJpaRepository repository) {
        super(repository);
    }

    // ── TX#1: сохранить в той же транзакции что и бизнес-данные ─────────────

    @Override
    @Transactional(transactionManager = "txBoxTransactionalManager")
    public void save(OutboxMessage message) {
        repository.save(OutboxEventEntity.from(message));
    }

    // ── TX#2: захват пачки ───────────────────────────────────────────────────

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "txBoxTransactionalManager")
    public List<OutboxMessage> claimBatch(int batchSize) {
        List<OutboxEventEntity> batch = repository.claimBatch(batchSize);
        batch.forEach(OutboxEventEntity::markInFlight);
        // flush внутри TX — обновляет статус до IN_FLIGHT до коммита
        repository.flush();
        return batch.stream().map(OutboxEventEntity::toMessage).toList();
    }

    // ── TX#3: применить результаты публикации ────────────────────────────────

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "txBoxTransactionalManager")
    public void applyOutcomes(List<PublishOutcome> outcomes, int maxRetries) {
        Map<UUID, OutboxEventEntity> entities = repository
                .findAllById(outcomes.stream().map(PublishOutcome::messageId).toList())
                .stream()
                .collect(Collectors.toMap(e -> e.getMessageId(), Function.identity()));

        for (PublishOutcome outcome : outcomes) {
            var entity = entities.get(outcome.messageId());
            if (entity == null) {
                log.warn("applyOutcomes: entity not found for messageId={}", outcome.messageId());
                continue;
            }

            switch (outcome) {
                case PublishOutcome.Published p -> {
                    entity.markProcessed();
                    log.debug("Published messageId={} to {}, latency={}ms",
                            p.messageId(), p.destination(), p.latency().toMillis());
                }
                case PublishOutcome.Retryable r -> {
                    if (entity.getAttempt() >= maxRetries) {
                        entity.markFailed("max retries exceeded: " + r.reason());
                        log.warn("FAILED messageId={} after {} attempts: {}",
                                r.messageId(), entity.getAttempt(), r.reason());
                    } else {
                        // Вернуть в PENDING — поллер подберёт на следующей итерации
                        entity.setStatus(MessageStatus.PENDING);
                        log.debug("Retryable messageId={}, attempt={}, reason={}",
                                r.messageId(), entity.getAttempt(), r.reason());
                    }
                }
                case PublishOutcome.Fatal f -> {
                    entity.markFailed(f.reason());
                    log.error("Fatal messageId={}: {}", f.messageId(), f.reason(), f.cause());
                }
                case PublishOutcome.Skipped sk -> {
                    entity.markSkipped(sk.reason());
                    log.debug("Skipped messageId={}: {}", sk.messageId(), sk.reason());
                }
            }
        }
    }

    // ── Maintenance ──────────────────────────────────────────────────────────

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "txBoxTransactionalManager")
    public int reclaimStale(Duration timeout) {
        Instant before = Instant.now().minus(timeout);
        int count = repository.reclaimStale(before);
        if (count > 0) log.info("Reclaimed {} stale IN_FLIGHT messages", count);
        return count;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "txBoxTransactionalManager")
    public void requeue(UUID messageId) {
        repository.findById(messageId).ifPresentOrElse(
                e -> {
                    e.setStatus(MessageStatus.PENDING);
                    log.info("Requeued messageId={}", messageId);
                },
                () -> log.warn("requeue: messageId={} not found", messageId)
        );
    }

    // ── AbstractJpaMessageStore abstracts ────────────────────────────────────

    @Override
    protected OutboxMessage toMessage(OutboxEventEntity entity) {
        return entity.toMessage();
    }

    @Override
    protected int doPurgeProcessed(Instant before, int limit) {
        return repository.purgeProcessed(before, Limit.of(limit));
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "txBoxTransactionalManager")
    public MessageStats getStats() {
        return new MessageStats(
                repository.countByStatus(MessageStatus.PENDING),
                repository.countByStatus(MessageStatus.IN_FLIGHT),
                0L, // inbox-specific
                repository.countByStatus(MessageStatus.PROCESSED),
                repository.countByStatus(MessageStatus.FAILED),
                repository.findOldestPendingCreatedAt()
        );
    }
}
