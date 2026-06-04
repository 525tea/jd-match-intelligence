package jobflow.collector.job.ingest;

public class JobPostingParserNotFoundException extends RuntimeException {

    public JobPostingParserNotFoundException(JobIngestionSource source) {
        super("Job posting parser not found. source=" + source);
    }
}
