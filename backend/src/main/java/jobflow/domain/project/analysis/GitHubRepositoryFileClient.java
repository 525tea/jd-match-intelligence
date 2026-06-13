package jobflow.domain.project.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import jobflow.domain.auth.oauth.token.OAuth2ProviderTokenService;
import jobflow.domain.user.AuthProvider;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GitHubRepositoryFileClient implements RepositoryFileClient {

    private static final String API_VERSION = "2022-11-28";
    private static final String ACCEPT = "application/vnd.github+json";
    private static final String BEARER_PREFIX = "Bearer ";

    private final OAuth2ProviderTokenService providerTokenService;
    private final RestClient restClient;

    @Autowired
    public GitHubRepositoryFileClient(OAuth2ProviderTokenService providerTokenService) {
        this(
                providerTokenService,
                RestClient.builder()
                        .baseUrl("https://api.github.com")
                        .defaultHeader(HttpHeaders.ACCEPT, ACCEPT)
                        .defaultHeader("X-GitHub-Api-Version", API_VERSION)
                        .build()
        );
    }

    GitHubRepositoryFileClient(
            OAuth2ProviderTokenService providerTokenService,
            RestClient restClient
    ) {
        this.providerTokenService = providerTokenService;
        this.restClient = restClient;
    }

    @Override
    public Optional<RepositoryFile> findFile(RepositoryRef repositoryRef, String path) {
        throw new BusinessException(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_NOT_FOUND);
    }

    @Override
    public Optional<RepositoryFile> findFile(
            Long userId,
            RepositoryRef repositoryRef,
            String path
    ) {
        validate(repositoryRef, path);
        String accessToken = providerTokenService.getRequiredAccessToken(userId, AuthProvider.GITHUB);

        try {
            ResponseEntity<GitHubContentsResponse> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment("repos", repositoryRef.owner(), repositoryRef.name(), "contents")
                            .path("/" + path)
                            .queryParam("ref", repositoryRef.ref())
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .retrieve()
                    .toEntity(GitHubContentsResponse.class);

            GitHubContentsResponse body = response.getBody();
            if (body == null || !body.isDecodableFile()) {
                return Optional.empty();
            }

            return Optional.of(new RepositoryFile(path, body.decodedContent()));
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientResponseException exception) {
            throw toGitHubException(exception);
        } catch (RestClientException exception) {
            throw new GitHubRepositoryFileClientException(
                    "GitHub repository file request failed",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    GitHubApiRateLimit.empty(),
                    exception
            );
        }
    }

    private void validate(RepositoryRef repositoryRef, String path) {
        if (repositoryRef == null) {
            throw new IllegalArgumentException("repositoryRef must not be null");
        }
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("path must not be blank");
        }
    }

    private GitHubRepositoryFileClientException toGitHubException(RestClientResponseException exception) {
        GitHubApiRateLimit rateLimit = GitHubApiRateLimit.from(exception.getResponseHeaders());
        String message = rateLimit.isExhausted()
                ? "GitHub API rate limit exceeded"
                : "GitHub repository file request failed";

        return new GitHubRepositoryFileClientException(
                message,
                exception.getStatusCode(),
                rateLimit,
                exception
        );
    }

    record GitHubContentsResponse(
            @JsonProperty("type") String type,
            @JsonProperty("encoding") String encoding,
            @JsonProperty("content") String content
    ) {

        private static final String FILE_TYPE = "file";
        private static final String BASE64_ENCODING = "base64";

        boolean isDecodableFile() {
            return FILE_TYPE.equals(type)
                    && BASE64_ENCODING.equalsIgnoreCase(encoding)
                    && StringUtils.hasText(content);
        }

        String decodedContent() {
            byte[] decoded = Base64.getMimeDecoder().decode(content);
            return new String(decoded, StandardCharsets.UTF_8);
        }
    }
}
