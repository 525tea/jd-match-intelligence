package jobflow.domain.outbox;

import java.util.List;
import java.time.LocalDateTime;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop100ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
            OutboxStatus status,
            int retryCount
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT event
            FROM OutboxEvent event
            WHERE event.status = :status
              AND event.retryCount < :retryCount
            ORDER BY event.createdAt ASC
            """)
    List<OutboxEvent> findRelayBatch(
            OutboxStatus status,
            int retryCount,
            Pageable pageable
    );

    @Query("""
            SELECT event.id
            FROM OutboxEvent event
            WHERE event.createdAt < :threshold
              AND (
                event.status = jobflow.domain.outbox.OutboxStatus.PUBLISHED
                OR (
                  event.status = jobflow.domain.outbox.OutboxStatus.PENDING
                  AND EXISTS (
                    SELECT 1
                    FROM ProcessedKafkaEvent processed
                    WHERE processed.eventId = event.id
                  )
                )
              )
            ORDER BY
              CASE
                WHEN event.status = jobflow.domain.outbox.OutboxStatus.PENDING THEN 0
                ELSE 1
              END,
              event.createdAt ASC
            """)
    List<Long> findCleanupCandidateIds(
            @Param("threshold") LocalDateTime threshold,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM OutboxEvent event WHERE event.id IN :ids")
    int deleteByIdIn(@Param("ids") List<Long> ids);
}
