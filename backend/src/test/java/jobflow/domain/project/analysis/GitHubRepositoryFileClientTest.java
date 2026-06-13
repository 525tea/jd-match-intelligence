package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import jobflow.domain.auth.oauth.token.OAuth2ProviderTokenService;
import jobflow.domain.user.AuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubRepositoryFileClientTest {

    private final OAuth2ProviderTokenService providerTokenService =
            mock(OAuth2ProviderTokenService.class);

    private RestClient restClient;
    private MockRestServiceServer server;
    private GitHubRepositoryFileClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.github.test");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        client = new GitHubRepositoryFileClient(providerTokenService, restClient);
    }

    @Test
    @DisplayName("GitHub contents API에서 repository file을 조회하고 base64 content를 디코딩한다")
    void findFile() {
        Long userId = 1L;
        RepositoryRef repositoryRef = new RepositoryRef("example-org", "sample-repo", "main");
        String path = "backend/build.gradle";
        given(providerTokenService.getRequiredAccessToken(userId, AuthProvider.GITHUB))
                .willReturn("test-provider-token");
        server.expect(once(), requestTo(
                        "https://api.github.test/repos/example-org/sample-repo/contents/backend/build.gradle?ref=main"
                ))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-provider-token"))
                .andRespond(withSuccess("""
                        {
                          "type": "file",
                          "encoding": "base64",
                          "content": "%s"
                        }
                        """.formatted(base64("plugins { id 'java' }")), MediaType.APPLICATION_JSON));

        Optional<RepositoryFile> result = client.findFile(userId, repositoryRef, path);

        assertThat(result).isPresent();
        assertThat(result.get().path()).isEqualTo(path);
        assertThat(result.get().content()).isEqualTo("plugins { id 'java' }");
        server.verify();
    }

    @Test
    @DisplayName("GitHub contents API에서 404가 내려오면 파일 없음으로 처리한다")
    void findFileNotFound() {
        Long userId = 1L;
        RepositoryRef repositoryRef = new RepositoryRef("example-org", "sample-repo", "main");
        given(providerTokenService.getRequiredAccessToken(userId, AuthProvider.GITHUB))
                .willReturn("test-provider-token");
        server.expect(once(), requestTo(
                        "https://api.github.test/repos/example-org/sample-repo/contents/missing.yml?ref=main"
                ))
                .andRespond(withResourceNotFound());

        Optional<RepositoryFile> result = client.findFile(userId, repositoryRef, "missing.yml");

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("GitHub API rate limit 응답은 metadata를 보존한 예외로 변환한다")
    void findFileRateLimited() {
        Long userId = 1L;
        RepositoryRef repositoryRef = new RepositoryRef("example-org", "sample-repo", "main");
        given(providerTokenService.getRequiredAccessToken(userId, AuthProvider.GITHUB))
                .willReturn("test-provider-token");
        server.expect(once(), requestTo(
                        "https://api.github.test/repos/example-org/sample-repo/contents/backend/build.gradle?ref=main"
                ))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Limit", "5000")
                        .header("X-RateLimit-Remaining", "0")
                        .header("X-RateLimit-Used", "5000")
                        .header("X-RateLimit-Reset", "1781316000")
                        .header("X-RateLimit-Resource", "core")
                        .body("{}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findFile(userId, repositoryRef, "backend/build.gradle"))
                .isInstanceOfSatisfying(GitHubRepositoryFileClientException.class, exception -> {
                    assertThat(exception).hasMessage("GitHub API rate limit exceeded");
                    assertThat(exception.statusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.rateLimit().remaining()).isZero();
                    assertThat(exception.rateLimit().resetAt()).isEqualTo(Instant.ofEpochSecond(1781316000));
                    assertThat(exception.rateLimit().resource()).isEqualTo("core");
                });
        server.verify();
    }

    @Test
    @DisplayName("userId 없는 legacy 호출은 provider token 없음 예외를 던진다")
    void findFileWithoutUserId() {
        RepositoryRef repositoryRef = new RepositoryRef("example-org", "sample-repo", "main");

        assertThatThrownBy(() -> client.findFile(repositoryRef, "backend/build.gradle"))
                .hasMessage("저장된 OAuth2 provider token을 찾을 수 없습니다.");
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
