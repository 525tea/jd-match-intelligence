package jobflow.collector.job.collect;

import jobflow.collector.job.ingest.CrawlerUrlCandidate;
import jobflow.collector.job.ingest.JobIngestionSource;
import jobflow.collector.job.ingest.JobPostingCollectionResult;
import jobflow.collector.job.ingest.JobPostingCollectionService;
import jobflow.collector.job.ingest.SitemapCrawlResult;
import jobflow.collector.job.ingest.SitemapCrawlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.collector", name = "enabled", havingValue = "true")
public class CollectorRunner implements ApplicationRunner {

    private final CollectorRunnerProperties collectorRunnerProperties;
    private final SitemapCrawlService sitemapCrawlService;
    private final JobPostingCollectionService jobPostingCollectionService;

    @Override
    public void run(ApplicationArguments args) {
        JobIngestionSource source = collectorRunnerProperties.sourceOrDefault();
        SitemapCrawlResult result = sitemapCrawlService.crawl(source);

        log.info(
                "Collector sitemap crawl completed. source={}, fetchedSitemapCount={}, discoveredJobUrlCount={}",
                result.source(),
                result.fetchedSitemapCount(),
                result.discoveredJobUrlCount()
        );

        result.jobUrls().stream()
                .limit(collectorRunnerProperties.previewLimitOrDefault())
                .forEach(this::logDiscoveredJobUrl);

        result.jobUrls().stream()
                .limit(collectorRunnerProperties.collectLimitOrDefault())
                .map(jobPostingCollectionService::collect)
                .forEach(this::logCollectionResult);
    }

    private void logDiscoveredJobUrl(CrawlerUrlCandidate candidate) {
        log.info(
                "Collector discovered job URL. source={}, externalId={}, detailUrl={}, sourceUrl={}",
                candidate.source(),
                candidate.externalId(),
                candidate.detailUrl(),
                candidate.sourceUrl()
        );
    }

    private void logCollectionResult(JobPostingCollectionResult result) {
        if (result.success()) {
            log.info(
                    "Collector job posting collected. source={}, externalId={}, resultType={}",
                    result.candidate().source(),
                    result.candidate().externalId(),
                    result.ingestionResultType()
            );
            return;
        }

        log.warn(
                "Collector job posting skipped. source={}, externalId={}, error={}",
                result.candidate().source(),
                result.candidate().externalId(),
                result.errorMessage()
        );
    }
}
