package io.txbox.producer.jpa.repository;

import io.txbox.core.model.MessageStatus;
import io.txbox.producer.jpa.entity.OutboxEventEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Захватывает пачку PENDING-записей с пессимистичной блокировкой SKIP LOCKED.
     * SKIP LOCKED — ключ к параллельному запуску нескольких поллеров без конкуренции.
     */
    @Query(value = """
            SELECT * FROM txbox_outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> claimBatch(@Param("limit") int limit);

    /**
     * Возвращает зависшие IN_FLIGHT записи в PENDING.
     * Вызывается из OutboxMaintenance по расписанию.
     */
    @Modifying
    @Query("""
            UPDATE OutboxEventEntity e
            SET e.status = io.txbox.core.model.MessageStatus.PENDING,
                e.claimedAt = null
            WHERE e.status = io.txbox.core.model.MessageStatus.IN_FLIGHT
              AND e.claimedAt < :before
            """)
    int reclaimStale(@Param("before") Instant before);

    /**
     * Удаляет завершённые записи старше порога — для OutboxMaintenance.purge().
     */
    @Modifying
    @Query("""
            DELETE FROM OutboxEventEntity e
            WHERE e.status IN (
                io.txbox.core.model.MessageStatus.PROCESSED,
                io.txbox.core.model.MessageStatus.FAILED,
                io.txbox.core.model.MessageStatus.SKIPPED
            )
            AND e.processedAt < :before
            """)
    int purgeProcessed(@Param("before") Instant before, Limit limit);

    /** Для health-check и метрик. */
    long countByStatus(MessageStatus status);

    @Query("SELECT MIN(e.createdAt) FROM OutboxEventEntity e WHERE e.status = 'PENDING'")
    Instant findOldestPendingCreatedAt();
}
