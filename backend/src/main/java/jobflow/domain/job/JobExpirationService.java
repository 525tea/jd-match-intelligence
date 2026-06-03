package jobflow.domain.job;

import jobflow.domain.outbox.OutboxEvent;
import jobflow.domain.outbox.OutboxEventService;
import jobflow.domain.outbox.OutboxEventTypes;
import jobflow.domain.outbox.payload.JobOutboxPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobExpirationService {

    private final JobRepository jobRepository;
    private final OutboxEventService outboxEventService;
    private final Clock clock;

    @Transactional
    public int expireOverdueJobs() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Job> overdueJobs = jobRepository.findByStatusAndDeadlineAtBefore(JobStatus.OPEN, now);

        overdueJobs.forEach(this::expireJob);

        return overdueJobs.size();
    }

    private void expireJob(Job job) {
        job.expire();

        outboxEventService.save(
                "JOB",
                job.getId(),
                OutboxEventTypes.JOB_EXPIRED,
                JobOutboxPayload.from(job),
                OutboxEvent.TOPIC_JOB_EVENTS
        );
    }
}
