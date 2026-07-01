package jobflow.domain.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedKafkaEventRepository extends JpaRepository<ProcessedKafkaEvent, Long> {

    boolean existsByConsumerNameAndEventId(String consumerName, Long eventId);
}
