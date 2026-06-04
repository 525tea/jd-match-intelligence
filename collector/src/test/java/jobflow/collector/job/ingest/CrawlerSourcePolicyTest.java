package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CrawlerSourcePolicyTest {

    @Test
    @DisplayName("허용된 path prefix에 속하면 true를 반환한다")
    void isAllowedPath() {
        CrawlerSourcePolicy policy = createPolicy();

        assertThat(policy.isAllowedPath("/recruit/joblist")).isTrue();
        assertThat(policy.isAllowedPath("/Recruit/GI_Read/12345")).isTrue();
    }

    @Test
    @DisplayName("차단된 path prefix에 속하면 false를 반환한다")
    void isAllowedPathWithDisallowedPath() {
        CrawlerSourcePolicy policy = createPolicy();

        assertThat(policy.isAllowedPath("/login/form")).isFalse();
        assertThat(policy.isAllowedPath("/Search/?stext=backend")).isFalse();
    }

    @Test
    @DisplayName("허용 prefix에 속하지 않으면 false를 반환한다")
    void isAllowedPathWithUnknownPath() {
        CrawlerSourcePolicy policy = createPolicy();

        assertThat(policy.isAllowedPath("/corp/dashboard")).isFalse();
    }

    @Test
    @DisplayName("빈 path는 false를 반환한다")
    void isAllowedPathWithBlankPath() {
        CrawlerSourcePolicy policy = createPolicy();

        assertThat(policy.isAllowedPath(null)).isFalse();
        assertThat(policy.isAllowedPath("")).isFalse();
        assertThat(policy.isAllowedPath(" ")).isFalse();
    }

    private CrawlerSourcePolicy createPolicy() {
        return new CrawlerSourcePolicy(
                JobIngestionSource.JOBKOREA,
                "https://www.jobkorea.co.kr",
                "https://www.jobkorea.co.kr/robots.txt",
                "https://www.jobkorea.co.kr/sitemap.xml",
                List.of("/recruit/joblist", "/Recruit/GI_Read"),
                List.of("/login/", "/Search/"),
                Duration.ofSeconds(20),
                1000
        );
    }
}
