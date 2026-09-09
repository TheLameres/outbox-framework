package io.txbox.consumer.jpa.repository;

import io.txbox.consumer.jpa.entity.InboxMessageEntity;
import io.txbox.core.model.MessageStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InboxJpaRepository extends JpaRepository<InboxMessageEntity, UUID> {

    /**
     * Поиск по (partition, offset) — главный ключ дедупликации.
     * Уникальный индекс в БД гарантирует отсутствие дублей даже при
     * параллельных consumer-инстансах.
     */
    Optional<InboxMessageEntity> findByPartitionAndOffset(int partition, long offset);

    /** Для health-check и метрик. */
    long countByStatus(MessageStatus status);

    /**
     * Oldest unprocessed — для метрики лага и health-check.
     */
    @Query("""
            SELECT MIN(e.receivedAt)
            FROM InboxMessageEntity e
            WHERE e.status = io.txbox.core.model.MessageStatus.RECEIVED
            """)
    Instant findOldestReceivedAt();

    /**
     * Удаляет завершённые записи старше порога.
     * PROCESSED и SKIPPED — удаляем; FAILED — оставляем для разбора.
     */
    @Modifying
    @Query("""
            DELETE FROM InboxMessageEntity e
            WHERE e.status IN (
                io.txbox.core.model.MessageStatus.PROCESSED,
                io.txbox.core.model.MessageStatus.SKIPPED
            )
            AND e.processedAt < :before
            """)
    int purgeProcessed(@Param("before") Instant before, Limit limit);
}
