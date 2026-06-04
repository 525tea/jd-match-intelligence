package jobflow.collector.job.ingest;

public interface JobPostingParser {

    boolean supports(JobIngestionSource source);

    IngestedJobPosting parse(FetchedJobPosting fetchedJobPosting);
}
