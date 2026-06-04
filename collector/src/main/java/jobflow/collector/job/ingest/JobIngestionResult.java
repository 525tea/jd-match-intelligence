package jobflow.collector.job.ingest;

import java.util.List;
import jobflow.collector.job.Job;

public record JobIngestionResult(
        JobIngestionResultType type,
        Job job,
        List<Job> duplicateCandidates
) {

    public JobIngestionResult(JobIngestionResultType type, Job job) {
        this(type, job, List.of());
    }

    public boolean hasDuplicateCandidates() {
        return !duplicateCandidates.isEmpty();
    }
}
