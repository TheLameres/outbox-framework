package com.company.outbox.jpa;

import com.company.outbox.core.OutboxMessage;
import com.company.outbox.core.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA-сущность. Единственное место во фреймворке, где остался Lombok:
 * record здесь невозможен — Hibernate нужен мутабельный класс с no-arg конструктором.
 *
 * <p>equals/hashCode — только по id: иначе hashCode меняется после flush()
 * и сущность «теряется» в HashSet.
 */
@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_claimable", columnList = "status, created_at"),
                @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OutboxEventEntity {

    @Id
    @ToString.Include
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100, updatable = false)
    @ToString.Include
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 255, updatable = false)
    @ToString.Include
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 150, updatable = false)
    @ToString.Include
    private String eventType;

    /** Payload не входит в toString: может быть на мегабайты. */
    @Column(columnDefinition = "text", nullable = false, updatable = false)
    private String payload;

    /** Hibernate 6.2+ маппит Map в jsonb нативно — hypersistence-utils не нужен. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> headers = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ToString.Include
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "attempt", nullable = false)
    @ToString.Include
    private int attempt = 0;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    /** Оптимистичная блокировка — защита от гонки поллера и ручного requeue из админки. */
    @Version
    @Column(nullable = false)
    private long lockVersion;

    // ---------- фабрика ----------

    public static OutboxEventEntity from(OutboxMessage message) {
        var entity = new OutboxEventEntity();
        entity.id = message.id();
        entity.aggregateType = message.aggregateType();
        entity.aggregateId = message.aggregateId();
        entity.eventType = message.eventType();
        entity.payload = message.payload();
        entity.headers = new LinkedHashMap<>(message.headers());
        entity.createdAt = message.createdAt();
        entity.attempt = message.attempt();
        entity.status = OutboxStatus.PENDING;
        return entity;
    }

    public OutboxMessage toMessage() {
        SequencedMap<String, String> hdrs = new LinkedHashMap<>(headers);
        return new OutboxMessage(
                id, aggregateType, aggregateId, eventType, payload, hdrs, createdAt, attempt);
    }

    // ---------- доменные переходы вместо анемичных сеттеров ----------

    public void claim() {
        this.status = OutboxStatus.IN_FLIGHT;
        this.claimedAt = Instant.now();
    }

    public void markProcessed() {
        this.status = OutboxStatus.PROCESSED;
        this.processedAt = Instant.now();
        this.lastError = null;
    }

    public void markRetryable(String reason, int maxRetries) {
        this.attempt++;
        this.lastError = truncate(reason);
        this.status = attempt >= maxRetries ? OutboxStatus.FAILED : OutboxStatus.PENDING;
    }

    public void markFailed(String reason) {
        this.status = OutboxStatus.FAILED;
        this.lastError = truncate(reason);
        this.processedAt = Instant.now();
    }

    public void requeue() {
        this.status = OutboxStatus.PENDING;
        this.attempt = 0;
        this.lastError = null;
        this.claimedAt = null;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 997) + "...";
    }
}
