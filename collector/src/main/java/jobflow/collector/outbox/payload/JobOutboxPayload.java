package jobflow.collector.outbox.payload;

import jobflow.collector.job.Job;
import jobflow.collector.job.JobStatus;

public record JobOutboxPayload(
        Long jobId,
        String source,
        String externalId,
        String canonicalFingerprint,
        String title,
        String companyName,
        JobStatus status
) {

    public static JobOutboxPayload from(Job job) {
        return new JobOutboxPayload(
                job.getId(),
                job.getSource(),
                job.getExternalId(),
                job.getCanonicalFingerprint(),
                job.getTitle(),
                job.getCompanyName(),
                job.getStatus()
        );
    }
}
