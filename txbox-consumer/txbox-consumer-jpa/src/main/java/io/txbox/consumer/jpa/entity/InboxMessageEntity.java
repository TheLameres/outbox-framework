package io.txbox.consumer.jpa.entity;

import io.txbox.core.model.InboxMessage;
import io.txbox.core.model.MessageStatus;
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
 * JPA-сущность inbox. Расширяет {@link AbstractMessageEntity},
 * добавляя consumer-специфичные поля: source, sourceId, receivedAt, partition, offset.
 *
 * <p>Уникальный индекс (partition, offset) — ключ идемпотентности:
 * гарантирует, что одно Kafka-сообщение обработается ровно один раз
 * даже при rebalance или replay.
 */
@Entity
@Table(
        name = "txbox_inbox_messages",
        indexes = {
                @Index(name = "idx_txbox_inbox_position",
                        columnList = "partition_idx, offset_val",
                        unique = true),
                @Index(name = "idx_txbox_inbox_status",
                        columnList = "status, received_at"),
                @Index(name = "idx_txbox_inbox_purge",
                        columnList = "processed_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
public class InboxMessageEntity extends AbstractMessageEntity {

    /** aggregateType от producer — откуда пришло событие. */
    @Column(name = "source", nullable = false, length = 100, updatable = false)
    private String source;

    /** aggregateId / Kafka message key. */
    @Column(name = "source_id", length = 255, updatable = false)
    private String sourceId;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    /**
     * Kafka partition — часть составного ключа дедупликации.
     * Колонка названа partition_idx чтобы не конфликтовать
     * с зарезервированным словом PARTITION в некоторых диалектах SQL.
     */
    @Column(name = "partition_idx", nullable = false, updatable = false)
    @ToString.Include
    private int partition;

    /**
     * Kafka offset — часть составного ключа дедупликации.
     * Colname offset_val — аналогично, OFFSET зарезервировано в SQL.
     */
    @Column(name = "offset_val", nullable = false, updatable = false)
    @ToString.Include
    private long offset;

    // ── маппинг ───────────────────────────────────────────────────────────────

    public static InboxMessageEntity from(InboxMessage m) {
        var e = new InboxMessageEntity();
        e.messageId  = m.messageId();
        e.source     = m.source();
        e.sourceId   = m.sourceId();
        e.eventType  = m.eventType();
        e.payload    = m.payload();
        e.headers    = new LinkedHashMap<>(m.headers());
        e.receivedAt = m.receivedAt();
        e.partition  = m.partition();
        e.offset     = m.offset();
        e.status     = MessageStatus.RECEIVED;
        return e;
    }

    public InboxMessage toMessage() {
        SequencedMap<String, String> hdrs = new LinkedHashMap<>(headers);
        return new InboxMessage(
                messageId, source, sourceId, eventType, payload, hdrs,
                /* timestamp = */ receivedAt, receivedAt, partition, offset);
    }
}
