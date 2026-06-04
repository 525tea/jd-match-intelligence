package jobflow.collector.job.ingest;

import org.springframework.stereotype.Service;

@Service
public class JobPostingFetchService {

    private final RobotsPolicyService robotsPolicyService;
    private final CrawlerHttpClient crawlerHttpClient;

    public JobPostingFetchService(
            RobotsPolicyService robotsPolicyService,
            CrawlerHttpClient crawlerHttpClient
    ) {
        this.robotsPolicyService = robotsPolicyService;
        this.crawlerHttpClient = crawlerHttpClient;
    }

    public FetchedJobPosting fetch(CrawlerUrlCandidate candidate) {
        robotsPolicyService.assertAllowed(candidate.source(), candidate.detailUrl());

        CrawlerHttpResponse response = crawlerHttpClient.get(candidate.detailUrl());

        if (!response.isSuccessful()) {
            throw new JobPostingFetchException(
                    "Failed to fetch job posting. source="
                            + candidate.source()
                            + ", externalId="
                            + candidate.externalId()
                            + ", detailUrl="
                            + candidate.detailUrl()
                            + ", statusCode="
                            + response.statusCode()
            );
        }

        return new FetchedJobPosting(
                candidate.source(),
                candidate.externalId(),
                candidate.sourceUrl(),
                candidate.detailUrl(),
                response.body()
        );
    }
}
