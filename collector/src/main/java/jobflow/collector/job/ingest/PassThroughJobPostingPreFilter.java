package jobflow.collector.job.ingest;

import org.springframework.stereotype.Component;

@Component
public class PassThroughJobPostingPreFilter implements JobPostingPreFilter {

    @Override
    public boolean supports(JobIngestionSource source) {
        return source == JobIngestionSource.JUMPIT
                || source == JobIngestionSource.WANTED;
    }

    @Override
    public boolean shouldSkip(FetchedJobPosting fetchedJobPosting) {
        return false;
    }
}
