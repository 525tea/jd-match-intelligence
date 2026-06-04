package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SitemapDiscoveryServiceTest {

    private final CrawlerProperties crawlerProperties = createProperties();

    private final SitemapDiscoveryService service = new SitemapDiscoveryService(
            crawlerProperties,
            new CrawlerUrlNormalizer(crawlerProperties)
    );

    @Test
    @DisplayName("sitemapindex에서 같은 host의 nested sitemap URL을 추출한다")
    void discoverNestedSitemaps() {
        ParsedSitemap sitemap = new ParsedSitemap(
                SitemapType.SITEMAP_INDEX,
                List.of(
                        new SitemapEntry("https://zighang.com/seo/sitemap/jobs-1.xml#top", LocalDateTime.now()),
                        new SitemapEntry("https://zighang.com/seo/sitemap/jobs-1.xml", LocalDateTime.now()),
                        new SitemapEntry("https://example.com/sitemap.xml", LocalDateTime.now())
                )
        );

        SitemapDiscoveryResult result = service.discover(JobIngestionSource.ZIGHANG, sitemap);

        assertThat(result.hasNestedSitemaps()).isTrue();
        assertThat(result.hasJobUrls()).isFalse();
        assertThat(result.sitemapUrls())
                .containsExactly("https://zighang.com/seo/sitemap/jobs-1.xml");
    }

    @Test
    @DisplayName("urlset에서 허용된 공고 URL 후보를 추출한다")
    void discoverJobUrls() {
        ParsedSitemap sitemap = new ParsedSitemap(
                SitemapType.URL_SET,
                List.of(
                        new SitemapEntry("https://jumpit.saramin.co.kr/position/123?utm=test", LocalDateTime.now()),
                        new SitemapEntry("https://jumpit.saramin.co.kr/position/123", LocalDateTime.now()),
                        new SitemapEntry("https://jumpit.saramin.co.kr/auth/login", LocalDateTime.now()),
                        new SitemapEntry("https://example.com/position/456", LocalDateTime.now())
                )
        );

        SitemapDiscoveryResult result = service.discover(JobIngestionSource.JUMPIT, sitemap);

        assertThat(result.hasNestedSitemaps()).isFalse();
        assertThat(result.hasJobUrls()).isTrue();
        assertThat(result.jobUrls()).hasSize(1);

        CrawlerUrlCandidate candidate = result.jobUrls().getFirst();

        assertThat(candidate.source()).isEqualTo(JobIngestionSource.JUMPIT);
        assertThat(candidate.externalId()).isEqualTo("123");
        assertThat(candidate.sourceUrl()).isEqualTo("https://jumpit.saramin.co.kr/position/123?utm=test");
        assertThat(candidate.detailUrl()).isEqualTo("https://jumpit.saramin.co.kr/position/123");
    }

    @Test
    @DisplayName("잡코리아는 허용된 공고 path만 후보로 추출한다")
    void discoverJobKoreaJobUrls() {
        ParsedSitemap sitemap = new ParsedSitemap(
                SitemapType.URL_SET,
                List.of(
                        new SitemapEntry("https://www.jobkorea.co.kr/Recruit/GI_Read/46789123", LocalDateTime.now()),
                        new SitemapEntry("https://www.jobkorea.co.kr/Search/?stext=java", LocalDateTime.now())
                )
        );

        SitemapDiscoveryResult result = service.discover(JobIngestionSource.JOBKOREA, sitemap);

        assertThat(result.jobUrls()).hasSize(1);
        assertThat(result.jobUrls().getFirst().externalId()).isEqualTo("46789123");
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

        sources.put(
                JobIngestionSource.JUMPIT,
                new CrawlerProperties.SourceProperties(
                        "https://jumpit.saramin.co.kr",
                        "https://jumpit.saramin.co.kr/robots.txt",
                        "https://jumpit.saramin.co.kr/sitemap.xml",
                        List.of("/"),
                        List.of("/resumes", "/auth/"),
                        Duration.ofSeconds(20),
                        1000
                )
        );

        sources.put(
                JobIngestionSource.JOBKOREA,
                new CrawlerProperties.SourceProperties(
                        "https://www.jobkorea.co.kr",
                        "https://www.jobkorea.co.kr/robots.txt",
                        "https://www.jobkorea.co.kr/sitemap.xml",
                        List.of("/recruit/joblist", "/Recruit/GI_Read"),
                        List.of("/login/", "/Search/"),
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
}
