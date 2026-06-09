package jobflow.collector.job.ingest;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SitemapDiscoveryService {

    private final CrawlerProperties crawlerProperties;
    private final CrawlerUrlNormalizer urlNormalizer;

    public SitemapDiscoveryService(
            CrawlerProperties crawlerProperties,
            CrawlerUrlNormalizer urlNormalizer
    ) {
        this.crawlerProperties = crawlerProperties;
        this.urlNormalizer = urlNormalizer;
    }

    public SitemapDiscoveryResult discover(JobIngestionSource source, ParsedSitemap sitemap) {
        if (sitemap.isSitemapIndex()) {
            return new SitemapDiscoveryResult(discoverNestedSitemaps(source, sitemap), List.of());
        }

        return new SitemapDiscoveryResult(List.of(), discoverJobUrls(source, sitemap));
    }

    private List<String> discoverNestedSitemaps(JobIngestionSource source, ParsedSitemap sitemap) {
        CrawlerSourcePolicy policy = crawlerProperties.policy(source);
        Map<String, String> urls = new LinkedHashMap<>();

        sitemap.entries().stream()
                .map(SitemapEntry::loc)
                .map(loc -> normalizeSitemapUrl(policy, loc))
                .flatMap(Optional::stream)
                .forEach(url -> urls.putIfAbsent(url, url));

        return List.copyOf(urls.values());
    }

    private List<CrawlerUrlCandidate> discoverJobUrls(JobIngestionSource source, ParsedSitemap sitemap) {
        Map<String, CrawlerUrlCandidate> candidates = new LinkedHashMap<>();

        sitemap.entries().stream()
                .map(SitemapEntry::loc)
                .map(loc -> urlNormalizer.normalize(source, loc))
                .flatMap(Optional::stream)
                .forEach(candidate -> candidates.putIfAbsent(
                        candidate.source() + ":" + candidate.externalId(),
                        candidate
                ));

        return List.copyOf(candidates.values());
    }

    private Optional<String> normalizeSitemapUrl(CrawlerSourcePolicy policy, String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }

        URI uri = URI.create(rawUrl.trim()).normalize();

        if (!isHttp(uri) || !isSameHost(policy.baseUrl(), uri)) {
            return Optional.empty();
        }

        return Optional.of(removeFragment(uri).toString());
    }

    private boolean isHttp(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme());
    }

    private boolean isSameHost(String baseUrl, URI uri) {
        String baseHost = URI.create(baseUrl).getHost();
        String uriHost = uri.getHost();

        return baseHost != null && baseHost.equalsIgnoreCase(uriHost);
    }

    private URI removeFragment(URI uri) {
        return URI.create(uri.toString().split("#", 2)[0]);
    }
}
