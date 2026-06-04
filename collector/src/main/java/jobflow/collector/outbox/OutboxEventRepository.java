package jobflow.collector.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop100ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
            OutboxStatus status,
            int retryCount
    );
}
