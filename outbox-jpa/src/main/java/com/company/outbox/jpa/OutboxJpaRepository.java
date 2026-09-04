package com.company.outbox.jpa;

import com.company.outbox.core.OutboxStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * SKIP LOCKED через JPA-хинт: {@code jakarta.persistence.lock.timeout = -2}
     * (это {@code org.hibernate.LockOptions.SKIP_LOCKED}).
     * Text block для JPQL — читаемость без конкатенации.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select e
              from OutboxEventEntity e
             where e.status = com.company.outbox.core.OutboxStatus.PENDING
             order by e.createdAt asc
            """)
    List<OutboxEventEntity> claimPending(Limit limit);

    /** Возврат «зависших» IN_FLIGHT после падения инстанса. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OutboxEventEntity e
               set e.status = com.company.outbox.core.OutboxStatus.PENDING,
                   e.claimedAt = null
             where e.status = com.company.outbox.core.OutboxStatus.IN_FLIGHT
               and e.claimedAt < :threshold
            """)
    int reclaimStale(@Param("threshold") Instant threshold);

    @Modifying
    @Query("""
            delete from OutboxEventEntity e
             where e.status = com.company.outbox.core.OutboxStatus.PROCESSED
               and e.processedAt < :threshold
            """)
    int purgeProcessedBefore(@Param("threshold") Instant threshold);

    long countByStatus(OutboxStatus status);

    List<OutboxEventEntity> findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
            String aggregateType, String aggregateId);
}
