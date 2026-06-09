package jobflow.collector.job.ingest;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobPostingCollectionService {

    private final JobPostingFetchService jobPostingFetchService;
    private final List<JobPostingPreFilter> jobPostingPreFilters;
    private final List<JobPostingParser> jobPostingParsers;
    private final JobIngestionService jobIngestionService;

    public JobPostingCollectionResult collect(CrawlerUrlCandidate candidate) {
        try {
            FetchedJobPosting fetchedJobPosting = jobPostingFetchService.fetch(candidate);

            if (findPreFilter(candidate.source()).shouldSkip(fetchedJobPosting)) {
                return JobPostingCollectionResult.success(
                        candidate,
                        JobIngestionResultType.SKIPPED
                );
            }

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

    private JobPostingPreFilter findPreFilter(JobIngestionSource source) {
        return jobPostingPreFilters.stream()
                .filter(preFilter -> preFilter.supports(source))
                .findFirst()
                .orElseThrow(() -> new JobPostingPreFilterNotFoundException(source));
    }

    private JobPostingParser findParser(JobIngestionSource source) {
        return jobPostingParsers.stream()
                .filter(parser -> parser.supports(source))
                .findFirst()
                .orElseThrow(() -> new JobPostingParserNotFoundException(source));
    }
}
