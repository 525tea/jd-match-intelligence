package jobflow.collector.job.ingest;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobPostingCollectionService {

    private final JobPostingFetchService jobPostingFetchService;
    private final List<JobPostingParser> jobPostingParsers;
    private final JobIngestionService jobIngestionService;

    public JobPostingCollectionResult collect(CrawlerUrlCandidate candidate) {
        try {
            FetchedJobPosting fetchedJobPosting = jobPostingFetchService.fetch(candidate);
            IngestedJobPosting ingestedJobPosting = findParser(candidate.source())
                    .parse(fetchedJobPosting);
            JobIngestionResult ingestionResult = jobIngestionService.ingest(ingestedJobPosting);

            return JobPostingCollectionResult.success(
                    candidate,
                    ingestionResult
            );
        } catch (Exception exception) {
            return JobPostingCollectionResult.failure(candidate, exception);
        }
    }

    private JobPostingParser findParser(JobIngestionSource source) {
        return jobPostingParsers.stream()
                .filter(parser -> parser.supports(source))
                .findFirst()
                .orElseThrow(() -> new JobPostingParserNotFoundException(source));
    }
}
