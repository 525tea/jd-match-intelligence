package jobflow.domain.outbox;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

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
}
