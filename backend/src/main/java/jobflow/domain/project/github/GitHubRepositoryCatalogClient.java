package jobflow.domain.project.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import jobflow.domain.auth.oauth.token.OAuth2ProviderTokenService;
import jobflow.domain.project.analysis.GitHubApiRateLimit;
import jobflow.domain.project.analysis.GitHubRepositoryFileClientException;
import jobflow.domain.user.AuthProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GitHubRepositoryCatalogClient {

    private static final String API_VERSION = "2022-11-28";
    private static final String ACCEPT = "application/vnd.github+json";
    private static final String BEARER_PREFIX = "Bearer ";

    private final OAuth2ProviderTokenService providerTokenService;
    private final RestClient restClient;

    @Autowired
    public GitHubRepositoryCatalogClient(OAuth2ProviderTokenService providerTokenService) {
        this(
                providerTokenService,
                RestClient.builder()
                        .baseUrl("https://api.github.com")
                        .defaultHeader(HttpHeaders.ACCEPT, ACCEPT)
                        .defaultHeader("X-GitHub-Api-Version", API_VERSION)
                        .build()
        );
    }

    GitHubRepositoryCatalogClient(
            OAuth2ProviderTokenService providerTokenService,
            RestClient restClient
    ) {
        this.providerTokenService = providerTokenService;
        this.restClient = restClient;
    }

    public List<GitHubRepositoryResponse> listRepositories(Long userId) {
        String accessToken = providerTokenService.getRequiredAccessToken(userId, AuthProvider.GITHUB);

        try {
            ResponseEntity<GitHubRepositoryApiResponse[]> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user/repos")
                            .queryParam("type", "all")
                            .queryParam("sort", "updated")
                            .queryParam("direction", "desc")
                            .queryParam("per_page", 100)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .retrieve()
                    .toEntity(GitHubRepositoryApiResponse[].class);

            GitHubRepositoryApiResponse[] body = response.getBody();
            if (body == null) {
                return List.of();
            }

            return Arrays.stream(body)
                    .filter(GitHubRepositoryApiResponse::hasRequiredFields)
                    .map(GitHubRepositoryApiResponse::toResponse)
                    .toList();
        } catch (RestClientResponseException exception) {
            throw toGitHubException(exception);
        } catch (RestClientException exception) {
            throw new GitHubRepositoryFileClientException(
                    "GitHub repository catalog request failed",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    GitHubApiRateLimit.empty(),
                    exception
            );
        }
    }

    private GitHubRepositoryFileClientException toGitHubException(RestClientResponseException exception) {
        GitHubApiRateLimit rateLimit = GitHubApiRateLimit.from(exception.getResponseHeaders());
        String message = rateLimit.isExhausted()
                ? "GitHub API rate limit exceeded"
                : "GitHub repository catalog request failed";

        return new GitHubRepositoryFileClientException(
                message,
                exception.getStatusCode(),
                rateLimit,
                exception
        );
    }

    record GitHubRepositoryApiResponse(
            @JsonProperty("name") String name,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("private") boolean privateRepository,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("description") String description,
            @JsonProperty("default_branch") String defaultBranch,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("owner") Owner owner
    ) {

        boolean hasRequiredFields() {
            return owner != null
                    && owner.login() != null
                    && !owner.login().isBlank()
                    && name != null
                    && !name.isBlank();
        }

        GitHubRepositoryResponse toResponse() {
            String normalizedFullName = fullName == null || fullName.isBlank()
                    ? owner.login() + "/" + name
                    : fullName;

            return new GitHubRepositoryResponse(
                    owner.login(),
                    name,
                    normalizedFullName,
                    defaultBranch == null || defaultBranch.isBlank() ? "HEAD" : defaultBranch,
                    privateRepository,
                    htmlUrl,
                    description,
                    updatedAt
            );
        }
    }

    record Owner(
            @JsonProperty("login") String login
    ) {
    }
}
