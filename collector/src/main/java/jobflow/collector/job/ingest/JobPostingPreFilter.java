package jobflow.collector.job.ingest;

public interface JobPostingPreFilter {

    boolean supports(JobIngestionSource source);

    boolean shouldSkip(FetchedJobPosting fetchedJobPosting);
}
