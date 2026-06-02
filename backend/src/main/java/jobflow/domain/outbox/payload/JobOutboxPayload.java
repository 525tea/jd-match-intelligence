package jobflow.domain.outbox.payload;

import jobflow.domain.job.Job;
import jobflow.domain.job.JobStatus;

public record JobOutboxPayload(
        Long jobId,
        String title,
        String companyName,
        JobStatus status
) {

    public static JobOutboxPayload from(Job job) {
        return new JobOutboxPayload(
                job.getId(),
                job.getTitle(),
                job.getCompanyName(),
                job.getStatus()
        );
    }
}
