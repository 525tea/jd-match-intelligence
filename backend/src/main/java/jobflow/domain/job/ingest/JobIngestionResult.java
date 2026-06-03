package jobflow.domain.job.ingest;

import jobflow.domain.job.Job;

public record JobIngestionResult(
        JobIngestionResultType type,
        Job job
) {
}
