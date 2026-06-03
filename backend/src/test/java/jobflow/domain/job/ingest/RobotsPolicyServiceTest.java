package jobflow.domain.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RobotsPolicyServiceTest {

    private final CrawlerProperties crawlerProperties = createProperties();
    private final FakeCrawlerHttpClient httpClient = new FakeCrawlerHttpClient();

    private final RobotsPolicyService service = new RobotsPolicyService(
            crawlerProperties,
            httpClient,
            new RobotsTxtParser()
    );

    @Test
    @DisplayName("robots.txt가 허용한 URL이면 true를 반환한다")
    void allowUrlAllowedByRobotsTxt() {
        httpClient.response = new CrawlerHttpResponse(200, """
                User-agent: *
                Disallow: /api/
                Disallow: /join
                """);

        boolean allowed = service.isAllowed(
                JobIngestionSource.ZIGHANG,
                "https://zighang.com/jobs/123"
        );

        assertThat(allowed).isTrue();
        assertThat(httpClient.requestedUrl).isEqualTo("https://zighang.com/robots.txt");
    }

    @Test
    @DisplayName("robots.txt가 차단한 URL이면 false를 반환한다")
    void rejectUrlDisallowedByRobotsTxt() {
        httpClient.response = new CrawlerHttpResponse(200, """
                User-agent: *
                Disallow: /api/
                """);

        boolean allowed = service.isAllowed(
                JobIngestionSource.ZIGHANG,
                "https://zighang.com/api/jobs/123"
        );

        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("다른 host URL은 false를 반환하고 robots.txt를 가져오지 않는다")
    void rejectDifferentHostUrl() {
        boolean allowed = service.isAllowed(
                JobIngestionSource.ZIGHANG,
                "https://example.com/jobs/123"
        );

        assertThat(allowed).isFalse();
        assertThat(httpClient.requestedUrl).isNull();
        assertThat(httpClient.requestCount).isZero();
    }

    @Test
    @DisplayName("robots.txt fetch 실패 시 예외가 발생한다")
    void failWhenRobotsTxtFetchFails() {
        httpClient.response = new CrawlerHttpResponse(403, "forbidden");

        assertThatThrownBy(() -> service.isAllowed(
                JobIngestionSource.ZIGHANG,
                "https://zighang.com/jobs/123"
        ))
                .isInstanceOf(RobotsPolicyException.class)
                .hasMessageContaining("statusCode=403");
    }

    @Test
    @DisplayName("assertAllowed는 차단 URL이면 예외가 발생한다")
    void assertAllowedThrowsWhenUrlIsDisallowed() {
        httpClient.response = new CrawlerHttpResponse(200, """
                User-agent: *
                Disallow: /api/
                """);

        assertThatThrownBy(() -> service.assertAllowed(
                JobIngestionSource.ZIGHANG,
                "https://zighang.com/api/jobs/123"
        ))
                .isInstanceOf(RobotsPolicyException.class)
                .hasMessageContaining("disallowed by robots.txt");
    }

    @Test
    @DisplayName("source별 robots.txt는 한 번 가져온 뒤 캐시한다")
    void cacheRobotsTxtBySource() {
        httpClient.response = new CrawlerHttpResponse(200, """
                User-agent: *
                Disallow: /api/
                """);

        assertThat(service.isAllowed(JobIngestionSource.ZIGHANG, "https://zighang.com/jobs/123")).isTrue();
        assertThat(service.isAllowed(JobIngestionSource.ZIGHANG, "https://zighang.com/api/jobs/123")).isFalse();

        assertThat(httpClient.requestCount).isEqualTo(1);
    }

    @Test
    @DisplayName("refresh를 호출하면 robots.txt를 다시 가져온다")
    void refreshRobotsTxt() {
        httpClient.response = new CrawlerHttpResponse(200, """
                User-agent: *
                Disallow: /api/
                """);

        assertThat(service.isAllowed(JobIngestionSource.ZIGHANG, "https://zighang.com/jobs/123")).isTrue();

        httpClient.response = new CrawlerHttpResponse(200, """
                User-agent: *
                Disallow: /jobs/
                """);

        service.refresh(JobIngestionSource.ZIGHANG);

        assertThat(service.isAllowed(JobIngestionSource.ZIGHANG, "https://zighang.com/jobs/123")).isFalse();
        assertThat(httpClient.requestCount).isEqualTo(2);
    }

    @Test
    @DisplayName("clearCache를 호출하면 다음 검증 때 robots.txt를 다시 가져온다")
    void clearRobotsTxtCache() {
        httpClient.response = new CrawlerHttpResponse(200, """
                User-agent: *
                Disallow: /api/
                """);

        service.isAllowed(JobIngestionSource.ZIGHANG, "https://zighang.com/jobs/123");
        service.clearCache();
        service.isAllowed(JobIngestionSource.ZIGHANG, "https://zighang.com/jobs/456");

        assertThat(httpClient.requestCount).isEqualTo(2);
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
                        Duration.ofSeconds(20),
                        1000
                )
        );

        return new CrawlerProperties(
                "JobFlowCrawler/0.1",
                Duration.ofSeconds(20),
                1000,
                sources
        );
    }

    private static class FakeCrawlerHttpClient implements CrawlerHttpClient {

        private String requestedUrl;
        private int requestCount;
        private CrawlerHttpResponse response = new CrawlerHttpResponse(200, "");

        @Override
        public CrawlerHttpResponse get(String url) {
            this.requestedUrl = url;
            this.requestCount++;

            return response;
        }
    }
}
