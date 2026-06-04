package jobflow.collector.job.ingest;

import java.net.URI;
import org.springframework.stereotype.Service;

@Service
public class SitemapFetchService {

    private final CrawlerProperties crawlerProperties;
    private final CrawlerHttpClient crawlerHttpClient;
    private final SitemapParser sitemapParser;

    public SitemapFetchService(
            CrawlerProperties crawlerProperties,
            CrawlerHttpClient crawlerHttpClient,
            SitemapParser sitemapParser
    ) {
        this.crawlerProperties = crawlerProperties;
        this.crawlerHttpClient = crawlerHttpClient;
        this.sitemapParser = sitemapParser;
    }

    public FetchedSitemap fetchRoot(JobIngestionSource source) {
        CrawlerSourcePolicy policy = crawlerProperties.policy(source);

        return fetch(source, policy.sitemapUrl());
    }

    public FetchedSitemap fetch(JobIngestionSource source, String sitemapUrl) {
        String normalizedUrl = normalizeAndValidate(source, sitemapUrl);
        CrawlerHttpResponse response = crawlerHttpClient.get(normalizedUrl);

        if (!response.isSuccessful()) {
            throw new SitemapFetchException(
                    "Failed to fetch sitemap. source="
                            + source
                            + ", url="
                            + normalizedUrl
                            + ", statusCode="
                            + response.statusCode()
            );
        }

        ParsedSitemap sitemap = sitemapParser.parse(response.body());

        return new FetchedSitemap(source, normalizedUrl, sitemap);
    }

    private String normalizeAndValidate(JobIngestionSource source, String sitemapUrl) {
        if (sitemapUrl == null || sitemapUrl.isBlank()) {
            throw new SitemapFetchException("Sitemap URL must not be blank. source=" + source);
        }

        CrawlerSourcePolicy policy = crawlerProperties.policy(source);
        URI uri = URI.create(sitemapUrl.trim()).normalize();

        if (!isHttp(uri)) {
            throw new SitemapFetchException(
                    "Only HTTP sitemap URL is allowed. source=" + source + ", url=" + sitemapUrl
            );
        }

        if (!isSameHost(policy.baseUrl(), uri)) {
            throw new SitemapFetchException(
                    "Sitemap URL host is not allowed. source=" + source + ", url=" + sitemapUrl
            );
        }

        return removeFragment(uri).toString();
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
