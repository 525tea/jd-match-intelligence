package jobflow.domain.outbox;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DlqMessageRepository extends JpaRepository<DlqMessage, Long> {

    List<DlqMessage> findTop100ByOrderByCreatedAtDesc();

    Optional<DlqMessage> findBySourceTopicAndSourcePartitionAndSourceOffset(
            String sourceTopic,
            int sourcePartition,
            long sourceOffset
    );
}
