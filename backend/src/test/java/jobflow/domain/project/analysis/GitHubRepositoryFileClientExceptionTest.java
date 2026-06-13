package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GitHubRepositoryFileClientExceptionTest {

    @Test
    @DisplayName("GitHub repository file client 예외는 HTTP status와 rate limit metadata를 보존한다")
    void exception() {
        GitHubApiRateLimit rateLimit = new GitHubApiRateLimit(
                5000L,
                0L,
                5000L,
                null,
                "core"
        );

        GitHubRepositoryFileClientException exception = new GitHubRepositoryFileClientException(
                "GitHub API rate limit exceeded",
                HttpStatus.FORBIDDEN,
                rateLimit
        );

        assertThat(exception).hasMessage("GitHub API rate limit exceeded");
        assertThat(exception.statusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exception.rateLimit()).isEqualTo(rateLimit);
        assertThat(exception.rateLimit().isExhausted()).isTrue();
    }

    @Test
    @DisplayName("rate limit metadata가 없으면 빈 metadata로 대체한다")
    void exceptionWithoutRateLimit() {
        GitHubRepositoryFileClientException exception = new GitHubRepositoryFileClientException(
                "GitHub API request failed",
                HttpStatus.INTERNAL_SERVER_ERROR,
                null
        );

        assertThat(exception.rateLimit()).isEqualTo(GitHubApiRateLimit.empty());
    }
}
