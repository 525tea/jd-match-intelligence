package jobflow.collector.job.ingest;

public class JobPostingPreFilterNotFoundException extends RuntimeException {

    public JobPostingPreFilterNotFoundException(JobIngestionSource source) {
        super("Job posting pre-filter not found. source=" + source);
    }
}
