package com.company.outbox.jpa;

import com.company.outbox.core.OutboxMessage;
import com.company.outbox.core.OutboxStore;
import com.company.outbox.core.PublishOutcome;
import lombok.RequiredArgsConstructor;
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

@Slf4j
@RequiredArgsConstructor
public class JpaOutboxStore implements OutboxStore {

    private final OutboxJpaRepository repository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(OutboxMessage message) {
        repository.save(OutboxEventEntity.from(message));
        log.debug("outbox: staged {} [{}]", message.eventType(), message.id());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveAll(List<OutboxMessage> messages) {
        repository.saveAll(messages.stream().map(OutboxEventEntity::from).toList());
    }

    @Override
    @Transactional
    public List<OutboxMessage> claimBatch(int batchSize) {
        var claimed = repository.claimPending(Limit.of(batchSize));
        claimed.forEach(OutboxEventEntity::claim);
        return claimed.stream().map(OutboxEventEntity::toMessage).toList();
    }

    @Override
    @Transactional
    public void applyOutcomes(List<PublishOutcome> outcomes, int maxRetries) {
        Map<UUID, OutboxEventEntity> byId = repository
                .findAllById(outcomes.stream().map(PublishOutcome::messageId).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        OutboxEventEntity::getId, Function.identity()));

        for (PublishOutcome outcome : outcomes) {
            var entity = byId.get(outcome.messageId());
            if (entity == null) {
                log.warn("outbox: outcome for unknown message {}", outcome.messageId());
                continue;
            }
            // Record patterns + exhaustive switch по sealed-иерархии:
            // добавление нового варианта PublishOutcome сломает компиляцию здесь.
            switch (outcome) {
                case PublishOutcome.Published(UUID id, String destination, Duration latency) -> {
                    entity.markProcessed();
                    log.debug("outbox: published {} -> {} in {}ms", id, destination, latency.toMillis());
                }
                case PublishOutcome.Skipped(UUID id, String reason) -> {
                    entity.markProcessed();
                    log.info("outbox: skipped {} ({})", id, reason);
                }
                case PublishOutcome.Retryable(UUID id, String reason, Throwable cause)
                        when entity.getAttempt() + 1 >= maxRetries -> {
                    entity.markFailed("retries exhausted: " + reason);
                    log.error("outbox: message {} moved to FAILED after {} attempts",
                            id, maxRetries, cause);
                }
                case PublishOutcome.Retryable(UUID id, String reason, Throwable ignored) -> {
                    entity.markRetryable(reason, maxRetries);
                    log.warn("outbox: retry {} attempt={} reason={}", id, entity.getAttempt(), reason);
                }
                case PublishOutcome.Fatal(UUID id, String reason, Throwable cause) -> {
                    entity.markFailed(reason);
                    log.error("outbox: fatal for {} — {}", id, reason, cause);
                }
            }
        }
    }

    @Override
    @Transactional
    public int purgeProcessed(Duration retention, int limit) {
        return repository.purgeProcessedBefore(Instant.now().minus(retention));
    }

    @Override
    @Transactional
    public void requeue(UUID messageId) {
        repository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("No outbox message " + messageId))
                .requeue();
    }

    /** Возврат зависших IN_FLIGHT — вызывается отдельным расписанием. */
    @Transactional
    public int reclaimStale(Duration timeout) {
        int reclaimed = repository.reclaimStale(Instant.now().minus(timeout));
        if (reclaimed > 0) {
            log.warn("outbox: reclaimed {} stale IN_FLIGHT messages", reclaimed);
        }
        return reclaimed;
    }
}
