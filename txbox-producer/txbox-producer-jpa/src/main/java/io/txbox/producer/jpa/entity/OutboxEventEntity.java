package io.txbox.producer.jpa.entity;

import io.txbox.core.model.MessageStatus;
import io.txbox.core.model.OutboxMessage;
import io.txbox.jpa.entity.AbstractMessageEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.SequencedMap;
import java.util.UUID;

/**
 * JPA-сущность outbox. Расширяет {@link AbstractMessageEntity},
 * добавляя producer-специфичные поля: aggregateType, aggregateId, createdAt, attempt, claimedAt.
 *
 * <p>Таблица переименована в {@code txbox_outbox_events} — Liquibase-миграция
 * в txbox-producer-starter выполняет ALTER TABLE при старте.
 */
@Entity
@Table(
        name = "txbox_outbox_events",
        indexes = {
                @Index(name = "idx_txbox_outbox_claimable", columnList = "status, created_at"),
                @Index(name = "idx_txbox_outbox_aggregate", columnList = "aggregate_type, aggregate_id"),
                @Index(name = "idx_txbox_outbox_purge",     columnList = "processed_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
public class OutboxEventEntity extends AbstractMessageEntity {

    @Column(name = "aggregate_type", nullable = false, length = 100, updatable = false)
    @ToString.Include
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 255, updatable = false)
    @ToString.Include
    private String aggregateId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    // ── setter нужен только для applyOutcomes (PENDING при retry) ────────────

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    // ── producer-специфичные переходы ─────────────────────────────────────

    public void markInFlight() {
        this.status    = MessageStatus.IN_FLIGHT;
        this.claimedAt = Instant.now();
        this.attempt++;
    }

    // ── маппинг ───────────────────────────────────────────────────────────

    public static OutboxEventEntity from(OutboxMessage m) {
        var e = new OutboxEventEntity();
        e.messageId     = m.messageId();
        e.aggregateType = m.aggregateType();
        e.aggregateId   = m.aggregateId();
        e.eventType     = m.eventType();
        e.payload       = m.payload();
        e.headers       = new LinkedHashMap<>(m.headers());
        e.createdAt     = m.timestamp();
        e.attempt       = m.attempt();
        e.status        = MessageStatus.PENDING;
        return e;
    }

    public OutboxMessage toMessage() {
        SequencedMap<String, String> hdrs = new LinkedHashMap<>(headers);
        return new OutboxMessage(
                messageId, aggregateType, aggregateId,
                eventType, payload, hdrs, createdAt, attempt);
    }
}
