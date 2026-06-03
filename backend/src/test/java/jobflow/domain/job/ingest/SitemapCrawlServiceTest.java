package jobflow.domain.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SitemapCrawlServiceTest {

    private final CrawlerProperties crawlerProperties = createProperties();

    private final FakeCrawlerHttpClient httpClient = new FakeCrawlerHttpClient();

    private final SitemapCrawlService service = new SitemapCrawlService(
            crawlerProperties,
            new SitemapFetchService(
                    crawlerProperties,
                    httpClient,
                    new SitemapParser()
            ),
            new SitemapDiscoveryService(crawlerProperties, new CrawlerUrlNormalizer(crawlerProperties))
    );

    @Test
    @DisplayName("root sitemap부터 nested sitemap까지 순회해 공고 URL 후보를 수집한다")
    void crawlSitemapTree() {
        httpClient.responses.put("https://zighang.com/seo/sitemap/sitemap-index.xml", new CrawlerHttpResponse(200, """
                <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <sitemap>
                        <loc>https://zighang.com/seo/sitemap/jobs-1.xml</loc>
                    </sitemap>
                    <sitemap>
                        <loc>https://zighang.com/seo/sitemap/jobs-2.xml</loc>
                    </sitemap>
                </sitemapindex>
                """));

        httpClient.responses.put("https://zighang.com/seo/sitemap/jobs-1.xml", new CrawlerHttpResponse(200, """
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url>
                        <loc>https://zighang.com/jobs/100</loc>
                    </url>
                    <url>
                        <loc>https://zighang.com/jobs/200?utm_source=sitemap</loc>
                    </url>
                </urlset>
                """));

        httpClient.responses.put("https://zighang.com/seo/sitemap/jobs-2.xml", new CrawlerHttpResponse(200, """
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url>
                        <loc>https://zighang.com/jobs/200</loc>
                    </url>
                    <url>
                        <loc>https://zighang.com/api/jobs/300</loc>
                    </url>
                </urlset>
                """));

        SitemapCrawlResult result = service.crawl(JobIngestionSource.ZIGHANG);

        assertThat(result.source()).isEqualTo(JobIngestionSource.ZIGHANG);
        assertThat(result.fetchedSitemapCount()).isEqualTo(3);
        assertThat(result.fetchedSitemapUrls()).containsExactly(
                "https://zighang.com/seo/sitemap/sitemap-index.xml",
                "https://zighang.com/seo/sitemap/jobs-1.xml",
                "https://zighang.com/seo/sitemap/jobs-2.xml"
        );
        assertThat(result.discoveredJobUrlCount()).isEqualTo(2);
        assertThat(result.jobUrls())
                .extracting(CrawlerUrlCandidate::externalId)
                .containsExactly("100", "200");
    }

    @Test
    @DisplayName("중복 nested sitemap은 한 번만 가져온다")
    void skipDuplicatedNestedSitemap() {
        httpClient.responses.put("https://zighang.com/seo/sitemap/sitemap-index.xml", new CrawlerHttpResponse(200, """
                <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <sitemap>
                        <loc>https://zighang.com/seo/sitemap/jobs-1.xml</loc>
                    </sitemap>
                    <sitemap>
                        <loc>https://zighang.com/seo/sitemap/jobs-1.xml#top</loc>
                    </sitemap>
                </sitemapindex>
                """));

        httpClient.responses.put("https://zighang.com/seo/sitemap/jobs-1.xml", new CrawlerHttpResponse(200, """
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url>
                        <loc>https://zighang.com/jobs/100</loc>
                    </url>
                </urlset>
                """));

        SitemapCrawlResult result = service.crawl(JobIngestionSource.ZIGHANG);

        assertThat(result.fetchedSitemapCount()).isEqualTo(2);
        assertThat(httpClient.requestedUrls).containsExactly(
                "https://zighang.com/seo/sitemap/sitemap-index.xml",
                "https://zighang.com/seo/sitemap/jobs-1.xml"
        );
    }

    private CrawlerProperties createProperties() {
        Map<JobIngestionSource, CrawlerProperties.SourceProperties> sources =
                new EnumMap<>(JobIngestionSource.class);

        sources.put(
                JobIngestionSource.ZIGHANG,
                new CrawlerProperties.SourceProperties(
                        "https://zighang.com",
                        "https://zighang.com/robots.txt",
                        "https://zighang.com/seo/sitemap/sitemap-index.xml",
                        List.of("/"),
                        List.of("/api/", "/short/", "/join", "/menu/"),
                        Duration.ZERO,
                        1000
                )
        );

        return new CrawlerProperties(
                "JobFlowCrawler/0.1",
                Duration.ZERO,
                1000,
                sources
        );
    }

    private static class FakeCrawlerHttpClient implements CrawlerHttpClient {

        private final Map<String, CrawlerHttpResponse> responses = new HashMap<>();
        private final List<String> requestedUrls = new java.util.ArrayList<>();

        @Override
        public CrawlerHttpResponse get(String url) {
            requestedUrls.add(url);

            return responses.getOrDefault(
                    url,
                    new CrawlerHttpResponse(404, "not found")
            );
        }
    }
}
