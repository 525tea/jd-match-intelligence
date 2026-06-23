package jobflow.domain.project.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import jobflow.domain.auth.oauth.token.OAuth2ProviderTokenService;
import jobflow.domain.project.analysis.GitHubRepositoryFileClientException;
import jobflow.domain.user.AuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubRepositoryCatalogClientTest {

    private final OAuth2ProviderTokenService providerTokenService =
            mock(OAuth2ProviderTokenService.class);

    private MockRestServiceServer server;
    private GitHubRepositoryCatalogClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.github.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GitHubRepositoryCatalogClient(providerTokenService, builder.build());
    }

    @Test
    @DisplayName("사용자 GitHub repository 목록을 최신순으로 조회한다")
    void listRepositories() {
        Long userId = 1L;
        given(providerTokenService.getRequiredAccessToken(userId, AuthProvider.GITHUB))
                .willReturn("test-provider-token");
        server.expect(once(), requestTo(
                        "https://api.github.test/user/repos?type=all&sort=updated&direction=desc&per_page=100"
                ))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-provider-token"))
                .andRespond(withSuccess("""
                        [
                          {
                            "name": "sample-repo",
                            "full_name": "example-org/sample-repo",
                            "private": true,
                            "html_url": "https://github.example/example-org/sample-repo",
                            "description": "sample repository",
                            "default_branch": "main",
                            "updated_at": "2026-06-23T01:02:03Z",
                            "owner": { "login": "example-org" }
                          },
                          {
                            "name": "public-api",
                            "full_name": "test-owner/public-api",
                            "private": false,
                            "html_url": "https://github.example/test-owner/public-api",
                            "description": null,
                            "default_branch": "develop",
                            "updated_at": "2026-06-22T01:02:03Z",
                            "owner": { "login": "test-owner" }
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<GitHubRepositoryResponse> result = client.listRepositories(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).owner()).isEqualTo("example-org");
        assertThat(result.get(0).name()).isEqualTo("sample-repo");
        assertThat(result.get(0).fullName()).isEqualTo("example-org/sample-repo");
        assertThat(result.get(0).defaultBranch()).isEqualTo("main");
        assertThat(result.get(0).privateRepository()).isTrue();
        assertThat(result.get(0).updatedAt()).isEqualTo(Instant.parse("2026-06-23T01:02:03Z"));
        assertThat(result.get(1).owner()).isEqualTo("test-owner");
        assertThat(result.get(1).privateRepository()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("GitHub rate limit 응답은 metadata를 보존한 예외로 변환한다")
    void listRepositoriesRateLimited() {
        Long userId = 1L;
        given(providerTokenService.getRequiredAccessToken(userId, AuthProvider.GITHUB))
                .willReturn("test-provider-token");
        server.expect(once(), requestTo(
                        "https://api.github.test/user/repos?type=all&sort=updated&direction=desc&per_page=100"
                ))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Limit", "5000")
                        .header("X-RateLimit-Remaining", "0")
                        .header("X-RateLimit-Used", "5000")
                        .header("X-RateLimit-Reset", "1781316000")
                        .header("X-RateLimit-Resource", "core")
                        .body("{}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.listRepositories(userId))
                .isInstanceOfSatisfying(GitHubRepositoryFileClientException.class, exception -> {
                    assertThat(exception).hasMessage("GitHub API rate limit exceeded");
                    assertThat(exception.statusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.rateLimit().remaining()).isZero();
                    assertThat(exception.rateLimit().resource()).isEqualTo("core");
                });
        server.verify();
    }
}
