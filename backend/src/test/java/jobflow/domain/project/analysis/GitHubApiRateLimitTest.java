package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class GitHubApiRateLimitTest {

    @Test
    @DisplayName("GitHub REST API rate limit 헤더를 파싱한다")
    void fromHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Limit", "5000");
        headers.add("X-RateLimit-Remaining", "4998");
        headers.add("X-RateLimit-Used", "2");
        headers.add("X-RateLimit-Reset", "1781316000");
        headers.add("X-RateLimit-Resource", "core");

        GitHubApiRateLimit rateLimit = GitHubApiRateLimit.from(headers);

        assertThat(rateLimit.limit()).isEqualTo(5000L);
        assertThat(rateLimit.remaining()).isEqualTo(4998L);
        assertThat(rateLimit.used()).isEqualTo(2L);
        assertThat(rateLimit.resetAt()).isEqualTo(Instant.ofEpochSecond(1781316000));
        assertThat(rateLimit.resource()).isEqualTo("core");
        assertThat(rateLimit.isExhausted()).isFalse();
    }

    @Test
    @DisplayName("remaining이 0이면 rate limit exhausted로 판단한다")
    void exhausted() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Remaining", "0");

        GitHubApiRateLimit rateLimit = GitHubApiRateLimit.from(headers);

        assertThat(rateLimit.isExhausted()).isTrue();
    }

    @Test
    @DisplayName("비어 있거나 숫자가 아닌 헤더는 null로 유지한다")
    void invalidHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Limit", "unknown");
        headers.add("X-RateLimit-Remaining", "");
        headers.add("X-RateLimit-Reset", "not-a-number");

        GitHubApiRateLimit rateLimit = GitHubApiRateLimit.from(headers);

        assertThat(rateLimit.limit()).isNull();
        assertThat(rateLimit.remaining()).isNull();
        assertThat(rateLimit.resetAt()).isNull();
        assertThat(rateLimit.isExhausted()).isFalse();
    }
}
