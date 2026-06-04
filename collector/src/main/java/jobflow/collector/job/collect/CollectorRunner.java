package jobflow.collector.job.collect;

import jobflow.collector.job.ingest.CrawlerUrlCandidate;
import jobflow.collector.job.ingest.JobIngestionSource;
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
}
