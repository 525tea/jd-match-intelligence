package jobflow.domain.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class OutboxCleanupService {

    static final Duration DEFAULT_RETENTION = Duration.ofHours(6);
    static final int DEFAULT_BATCH_SIZE = 500;

    private final OutboxEventRepository outboxEventRepository;
    private final Clock clock;
    private final Duration retention;
    private final int batchSize;

    public OutboxCleanupService(
            OutboxEventRepository outboxEventRepository,
            Clock clock,
            @Value("${jobflow.outbox.cleanup.retention:PT6H}") Duration retention,
            @Value("${jobflow.outbox.cleanup.batch-size:" + DEFAULT_BATCH_SIZE + "}") int batchSize
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.clock = clock;
        this.retention = retention.isNegative() ? DEFAULT_RETENTION : retention;
        this.batchSize = Math.max(1, batchSize);
    }

    @Transactional
    public int cleanupProcessedEvents() {
        LocalDateTime threshold = LocalDateTime.now(clock).minus(retention);
        List<Long> candidateIds = outboxEventRepository.findCleanupCandidateIds(
                threshold,
                PageRequest.of(0, batchSize)
        );

        if (candidateIds.isEmpty()) {
            return 0;
        }

        int deletedCount = outboxEventRepository.deleteByIdIn(candidateIds);
        log.info(
                "Outbox cleanup completed. retention={}, batchSize={}, candidateCount={}, deletedCount={}",
                retention,
                batchSize,
                candidateIds.size(),
                deletedCount
        );
        return deletedCount;
    }
}
