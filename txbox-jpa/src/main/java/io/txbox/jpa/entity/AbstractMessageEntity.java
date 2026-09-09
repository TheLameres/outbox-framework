package io.txbox.jpa.entity;

import io.txbox.core.model.MessageStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Базовый @MappedSuperclass для обеих JPA-сущностей:
 * OutboxEventEntity (producer) и InboxMessageEntity (consumer).
 *
 * <p>Lombok сохраняется здесь, потому что Hibernate требует мутабельный класс
 * с no-arg конструктором — record не подходит.
 *
 * <p>equals/hashCode по {@code messageId}: hashCode не меняется после flush().
 */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public abstract class AbstractMessageEntity {

    @Id
    @ToString.Include
    protected UUID messageId;

    @Column(name = "event_type", nullable = false, length = 150, updatable = false)
    @ToString.Include
    protected String eventType;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    protected String payload;

    /**
     * Hibernate 6.2+ нативно маппит Map в jsonb — hypersistence-utils не нужен.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    protected Map<String, String> headers = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ToString.Include
    protected MessageStatus status;

    @Column(name = "processed_at")
    protected Instant processedAt;

    @Column(name = "last_error", length = 2000)
    protected String lastError;

    /**
     * Оптимистичная блокировка — защита от гонки поллера и ручного requeue.
     */
    @Version
    @Column(name = "lock_version", nullable = false)
    protected long lockVersion;

    // ── доменные переходы (используются в обоих наследниках) ─────────────────

    protected static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    public void markProcessed() {
        this.status = MessageStatus.PROCESSED;
        this.processedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(String reason) {
        this.status = MessageStatus.FAILED;
        this.lastError = truncate(reason, 2000);
        this.processedAt = Instant.now();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    public void markSkipped(String reason) {
        this.status = MessageStatus.SKIPPED;
        this.lastError = truncate(reason, 2000);
        this.processedAt = Instant.now();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AbstractMessageEntity other)) return false;
        return messageId != null && messageId.equals(other.messageId);
    }

    @Override
    public int hashCode() {
        // стабильный hashCode даже до persist
        return messageId == null ? System.identityHashCode(this) : messageId.hashCode();
    }
}
