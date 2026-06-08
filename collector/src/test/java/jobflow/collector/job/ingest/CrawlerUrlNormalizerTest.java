package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CrawlerUrlNormalizerTest {

    private final CrawlerUrlNormalizer normalizer = new CrawlerUrlNormalizer(createProperties());

    @Test
    @DisplayName("허용된 절대 URL을 정규화한다")
    void normalizeAbsoluteUrl() {
        String rawUrl = "https://www.jobkorea.co.kr/Recruit/GI_Read/46789123?utm_source=test#top";

        CrawlerUrlCandidate candidate = normalizer.normalize(JobIngestionSource.JOBKOREA, rawUrl)
                .orElseThrow();

        assertThat(candidate.source()).isEqualTo(JobIngestionSource.JOBKOREA);
        assertThat(candidate.externalId()).isEqualTo("46789123");
        assertThat(candidate.sourceUrl()).isEqualTo("https://www.jobkorea.co.kr/Recruit/GI_Read/46789123?utm_source=test");
        assertThat(candidate.detailUrl()).isEqualTo("https://www.jobkorea.co.kr/Recruit/GI_Read/46789123");
    }

    @Test
    @DisplayName("상대 URL을 baseUrl 기준으로 정규화한다")
    void normalizeRelativeUrl() {
        CrawlerUrlCandidate candidate = normalizer.normalize(
                        JobIngestionSource.JUMPIT,
                        "/position/12345?utm=test"
                )
                .orElseThrow();

        assertThat(candidate.externalId()).isEqualTo("12345");
        assertThat(candidate.sourceUrl()).isEqualTo("https://jumpit.saramin.co.kr/position/12345?utm=test");
        assertThat(candidate.detailUrl()).isEqualTo("https://jumpit.saramin.co.kr/position/12345");
    }

    @Test
    @DisplayName("점핏 상세 공고 URL만 후보로 정규화한다")
    void normalizeJumpitPositionUrl() {
        CrawlerUrlCandidate candidate = normalizer.normalize(
                        JobIngestionSource.JUMPIT,
                        "https://jumpit.saramin.co.kr/position/12345?utm=test"
                )
                .orElseThrow();

        assertThat(candidate.externalId()).isEqualTo("12345");
        assertThat(candidate.sourceUrl()).isEqualTo("https://jumpit.saramin.co.kr/position/12345?utm=test");
        assertThat(candidate.detailUrl()).isEqualTo("https://jumpit.saramin.co.kr/position/12345");
    }

    @Test
    @DisplayName("점핏 직무 탐색 페이지는 공고 후보에서 제외한다")
    void ignoreJumpitPositionLandingPage() {
        assertThat(normalizer.normalize(
                JobIngestionSource.JUMPIT,
                "https://jumpit.saramin.co.kr/position"
        )).isEmpty();
    }

    @Test
    @DisplayName("직행 상세 공고 UUID URL만 후보로 정규화한다")
    void normalizeZighangRecruitmentUrl() {
        CrawlerUrlCandidate candidate = normalizer.normalize(
                        JobIngestionSource.ZIGHANG,
                        "https://zighang.com/recruitment/c4de6576-efef-45cb-aff3-9b5e6f06fd76?utm=test"
                )
                .orElseThrow();

        assertThat(candidate.externalId()).isEqualTo("c4de6576-efef-45cb-aff3-9b5e6f06fd76");
        assertThat(candidate.detailUrl())
                .isEqualTo("https://zighang.com/recruitment/c4de6576-efef-45cb-aff3-9b5e6f06fd76");
    }

    @Test
    @DisplayName("차단 path는 후보에서 제외한다")
    void normalizeDisallowedPath() {
        assertThat(normalizer.normalize(
                JobIngestionSource.JOBKOREA,
                "https://www.jobkorea.co.kr/login/form"
        )).isEmpty();
    }

    @Test
    @DisplayName("다른 host URL은 후보에서 제외한다")
    void normalizeDifferentHost() {
        assertThat(normalizer.normalize(
                JobIngestionSource.JOBKOREA,
                "https://example.com/Recruit/GI_Read/46789123"
        )).isEmpty();
    }

    @Test
    @DisplayName("사람인 URL은 rec_idx를 유지한 detailUrl로 정규화한다")
    void normalizeSaraminUrl() {
        CrawlerUrlCandidate candidate = normalizer.normalize(
                        JobIngestionSource.SARAMIN,
                        "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=53645877&utm_source=test"
                )
                .orElseThrow();

        assertThat(candidate.externalId()).isEqualTo("53645877");
        assertThat(candidate.detailUrl()).isEqualTo("https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=53645877");
    }

    private CrawlerProperties createProperties() {
        Map<JobIngestionSource, CrawlerProperties.SourceProperties> sources =
                new EnumMap<>(JobIngestionSource.class);

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
                JobIngestionSource.ZIGHANG,
                new CrawlerProperties.SourceProperties(
                        "https://zighang.com",
                        "https://zighang.com/robots.txt",
                        "https://zighang.com/seo/sitemap/sitemap-index.xml",
                        List.of("/recruitment", "/jobs", "/job", "/recruit", "/seo/sitemap"),
                        List.of("/api", "/short", "/join", "/menu"),
                        Duration.ofSeconds(20),
                        1000
                )
        );

        sources.put(
                JobIngestionSource.SARAMIN,
                new CrawlerProperties.SourceProperties(
                        "https://www.saramin.co.kr",
                        "https://www.saramin.co.kr/robots.txt",
                        "https://www.saramin.co.kr/sitemap.xml",
                        List.of("/"),
                        List.of(),
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
