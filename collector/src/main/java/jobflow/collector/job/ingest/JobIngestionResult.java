package jobflow.collector.job.ingest;

import jobflow.collector.job.Job;

public record JobIngestionResult(
        JobIngestionResultType type,
        Job job
) {
}
