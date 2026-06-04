package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SitemapFetchServiceTest {

    private final FakeCrawlerHttpClient httpClient = new FakeCrawlerHttpClient();

    private final SitemapFetchService service = new SitemapFetchService(
            createProperties(),
            httpClient,
            new SitemapParser()
    );

    @Test
    @DisplayName("root sitemap URL을 가져와 파싱한다")
    void fetchRootSitemap() {
        httpClient.response = new CrawlerHttpResponse(200, """
                <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <sitemap>
                        <loc>https://zighang.com/seo/sitemap/jobs-1.xml</loc>
                    </sitemap>
                </sitemapindex>
                """);

        FetchedSitemap fetched = service.fetchRoot(JobIngestionSource.ZIGHANG);

        assertThat(httpClient.requestedUrl).isEqualTo("https://zighang.com/seo/sitemap/sitemap-index.xml");
        assertThat(fetched.source()).isEqualTo(JobIngestionSource.ZIGHANG);
        assertThat(fetched.sitemapUrl()).isEqualTo("https://zighang.com/seo/sitemap/sitemap-index.xml");
        assertThat(fetched.sitemap().isSitemapIndex()).isTrue();
        assertThat(fetched.sitemap().entries()).hasSize(1);
        assertThat(fetched.sitemap().entries().getFirst().loc())
                .isEqualTo("https://zighang.com/seo/sitemap/jobs-1.xml");
    }

    @Test
    @DisplayName("sitemap URL fragment를 제거한다")
    void removeSitemapUrlFragment() {
        httpClient.response = new CrawlerHttpResponse(200, """
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url>
                        <loc>https://zighang.com/jobs/123</loc>
                    </url>
                </urlset>
                """);

        FetchedSitemap fetched = service.fetch(
                JobIngestionSource.ZIGHANG,
                "https://zighang.com/seo/sitemap/jobs-1.xml#top"
        );

        assertThat(httpClient.requestedUrl).isEqualTo("https://zighang.com/seo/sitemap/jobs-1.xml");
        assertThat(fetched.sitemapUrl()).isEqualTo("https://zighang.com/seo/sitemap/jobs-1.xml");
    }

    @Test
    @DisplayName("다른 host의 sitemap URL은 가져오지 않는다")
    void rejectDifferentHostSitemapUrl() {
        assertThatThrownBy(() -> service.fetch(
                JobIngestionSource.ZIGHANG,
                "https://example.com/sitemap.xml"
        ))
                .isInstanceOf(SitemapFetchException.class)
                .hasMessageContaining("host is not allowed");

        assertThat(httpClient.requestedUrl).isNull();
    }

    @Test
    @DisplayName("HTTP 응답이 실패하면 예외가 발생한다")
    void failWhenHttpResponseIsNotSuccessful() {
        httpClient.response = new CrawlerHttpResponse(429, "too many requests");

        assertThatThrownBy(() -> service.fetchRoot(JobIngestionSource.ZIGHANG))
                .isInstanceOf(SitemapFetchException.class)
                .hasMessageContaining("statusCode=429");
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
                        Duration.ofMillis(0),
                        1000
                )
        );

        return new CrawlerProperties(
                "JobFlowCrawler/0.1",
                Duration.ofMillis(0),
                1000,
                sources
        );
    }

    private static class FakeCrawlerHttpClient implements CrawlerHttpClient {

        private String requestedUrl;
        private CrawlerHttpResponse response = new CrawlerHttpResponse(200, """
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"></urlset>
                """);

        @Override
        public CrawlerHttpResponse get(String url) {
            this.requestedUrl = url;

            return response;
        }
    }
}
