package jobflow.collector.job.ingest;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SitemapCrawlService {

    private static final int MAX_SITEMAP_FETCHES_PER_RUN = 100;

    private final CrawlerProperties crawlerProperties;
    private final SitemapFetchService sitemapFetchService;
    private final SitemapDiscoveryService sitemapDiscoveryService;

    public SitemapCrawlService(
            CrawlerProperties crawlerProperties,
            SitemapFetchService sitemapFetchService,
            SitemapDiscoveryService sitemapDiscoveryService
    ) {
        this.crawlerProperties = crawlerProperties;
        this.sitemapFetchService = sitemapFetchService;
        this.sitemapDiscoveryService = sitemapDiscoveryService;
    }

    public SitemapCrawlResult crawl(JobIngestionSource source) {
        return crawl(source, Integer.MAX_VALUE);
    }

    public SitemapCrawlResult crawl(JobIngestionSource source, int targetJobUrlCount) {
        CrawlerSourcePolicy policy = crawlerProperties.policy(source);
        Queue<String> pendingSitemapUrls = new ArrayDeque<>();
        Set<String> fetchedSitemapUrls = new LinkedHashSet<>();
        Map<String, CrawlerUrlCandidate> jobUrlCandidates = new LinkedHashMap<>();

        pendingSitemapUrls.add(policy.sitemapUrl());

        int targetCount = Math.max(targetJobUrlCount, 1);

        while (!pendingSitemapUrls.isEmpty()
                && fetchedSitemapUrls.size() < MAX_SITEMAP_FETCHES_PER_RUN
                && jobUrlCandidates.size() < targetCount) {
            String sitemapUrl = pendingSitemapUrls.poll();

            if (fetchedSitemapUrls.contains(sitemapUrl)) {
                continue;
            }

            FetchedSitemap fetchedSitemap = sitemapFetchService.fetch(source, sitemapUrl);
            fetchedSitemapUrls.add(fetchedSitemap.sitemapUrl());

            SitemapDiscoveryResult discoveryResult = sitemapDiscoveryService.discover(
                    source,
                    fetchedSitemap.sitemap()
            );

            if (jobUrlCandidates.size() < targetCount) {
                discoveryResult.sitemapUrls().stream()
                        .filter(url -> !fetchedSitemapUrls.contains(url))
                        .forEach(pendingSitemapUrls::add);
            }

            discoveryResult.jobUrls()
                    .forEach(candidate -> jobUrlCandidates.putIfAbsent(
                            candidate.source() + ":" + candidate.externalId(),
                            candidate
                    ));
        }

        return new SitemapCrawlResult(
                source,
                fetchedSitemapUrls.size(),
                List.copyOf(fetchedSitemapUrls),
                List.copyOf(jobUrlCandidates.values())
        );
    }
}
