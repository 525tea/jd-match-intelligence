package jobflow.collector.job.collect;

import jobflow.collector.job.ingest.CrawlerUrlCandidate;
import jobflow.collector.job.ingest.JobIngestionResultType;
import jobflow.collector.job.ingest.JobIngestionSource;
import jobflow.collector.job.ingest.JobPostingCollectionResult;
import jobflow.collector.job.ingest.JobPostingCollectionService;
import jobflow.collector.job.ingest.SitemapCrawlResult;
import jobflow.collector.job.ingest.SitemapCrawlService;
import jobflow.collector.job.ingest.WantedJobUrlDiscoveryService;
import java.util.List;
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
    private final WantedJobUrlDiscoveryService wantedJobUrlDiscoveryService;
    private final JobPostingCollectionService jobPostingCollectionService;

    @Override
    public void run(ApplicationArguments args) {
        JobIngestionSource source = collectorRunnerProperties.sourceOrDefault();
        int previewLimit = collectorRunnerProperties.previewLimitOrDefault();
        int collectLimit = collectorRunnerProperties.collectLimitOrDefault();
        int scanLimit = collectorRunnerProperties.scanLimitOrDefault();

        log.info(
                "Collector started. source={}, previewLimit={}, collectLimit={}, scanLimit={}",
                source,
                previewLimit,
                collectLimit,
                scanLimit
        );

        List<CrawlerUrlCandidate> jobUrls = discoverJobUrls(source, scanLimit);

        jobUrls.stream()
                .limit(previewLimit)
                .forEach(this::logDiscoveredJobUrl);

        CollectionSummary summary = collectUntilLimit(jobUrls, collectLimit, scanLimit);

        log.info(
                "Collector completed. source={}, processedCount={}, collectedCount={}, skippedCount={}, failedCount={}",
                source,
                summary.processedCount(),
                summary.collectedCount(),
                summary.skippedCount(),
                summary.failedCount()
        );
    }

    private List<CrawlerUrlCandidate> discoverJobUrls(JobIngestionSource source, int scanLimit) {
        if (source == JobIngestionSource.WANTED) {
            List<CrawlerUrlCandidate> jobUrls = wantedJobUrlDiscoveryService.discover(scanLimit);

            log.info(
                    "Collector wanted discovery completed. source={}, discoveredJobUrlCount={}",
                    source,
                    jobUrls.size()
            );

            return jobUrls;
        }

        SitemapCrawlResult result = sitemapCrawlService.crawl(source, scanLimit);

        log.info(
                "Collector sitemap crawl completed. source={}, fetchedSitemapCount={}, discoveredJobUrlCount={}",
                result.source(),
                result.fetchedSitemapCount(),
                result.discoveredJobUrlCount()
        );

        return result.jobUrls();
    }

    private CollectionSummary collectUntilLimit(
            List<CrawlerUrlCandidate> jobUrls,
            int collectLimit,
            int scanLimit
    ) {
        int processedCount = 0;
        int collectedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (CrawlerUrlCandidate candidate : jobUrls) {
            if (processedCount >= scanLimit || collectedCount >= collectLimit) {
                break;
            }

            processedCount++;
            JobPostingCollectionResult collectionResult = jobPostingCollectionService.collect(candidate);
            logCollectionResult(collectionResult);

            if (!collectionResult.success()) {
                failedCount++;
                continue;
            }

            if (collectionResult.ingestionResultType() == JobIngestionResultType.SKIPPED) {
                skippedCount++;
                continue;
            }

            collectedCount++;
        }

        return new CollectionSummary(
                processedCount,
                collectedCount,
                skippedCount,
                failedCount
        );
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
        if (!result.success()) {
            log.warn(
                    "Collector job posting failed. source={}, externalId={}, error={}",
                    result.candidate().source(),
                    result.candidate().externalId(),
                    result.errorMessage()
            );
            return;
        }

        if (result.ingestionResultType() == JobIngestionResultType.SKIPPED) {
            log.info(
                    "Collector job posting skipped. source={}, externalId={}, resultType={}",
                    result.candidate().source(),
                    result.candidate().externalId(),
                    result.ingestionResultType()
            );
            return;
        }

        log.info(
                "Collector job posting collected. source={}, externalId={}, resultType={}, duplicateCandidateCount={}",
                result.candidate().source(),
                result.candidate().externalId(),
                result.ingestionResultType(),
                result.duplicateCandidateCount()
        );
    }

    private record CollectionSummary(
            int processedCount,
            int collectedCount,
            int skippedCount,
            int failedCount
    ) {
    }
}
