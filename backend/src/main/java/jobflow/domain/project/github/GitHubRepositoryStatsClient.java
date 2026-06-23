package jobflow.domain.project.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jobflow.domain.auth.oauth.token.OAuth2ProviderTokenService;
import jobflow.domain.project.analysis.GitHubApiRateLimit;
import jobflow.domain.project.analysis.GitHubRepositoryFileClientException;
import jobflow.domain.project.analysis.RepositoryAnalysisStats;
import jobflow.domain.project.analysis.RepositoryRef;
import jobflow.domain.user.AuthProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GitHubRepositoryStatsClient {

    private static final String API_VERSION = "2022-11-28";
    private static final String ACCEPT = "application/vnd.github+json";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OAuth2ProviderTokenService providerTokenService;
    private final RestClient restClient;

    @Autowired
    public GitHubRepositoryStatsClient(OAuth2ProviderTokenService providerTokenService) {
        this(
                providerTokenService,
                RestClient.builder()
                        .baseUrl("https://api.github.com")
                        .defaultHeader(HttpHeaders.ACCEPT, ACCEPT)
                        .defaultHeader("X-GitHub-Api-Version", API_VERSION)
                        .build()
        );
    }

    GitHubRepositoryStatsClient(
            OAuth2ProviderTokenService providerTokenService,
            RestClient restClient
    ) {
        this.providerTokenService = providerTokenService;
        this.restClient = restClient;
    }

    public RepositoryAnalysisStats getRepositoryStats(Long userId, RepositoryRef repositoryRef) {
        if (repositoryRef == null) {
            throw new IllegalArgumentException("repositoryRef must not be null");
        }

        String accessToken = providerTokenService.getRequiredAccessToken(userId, AuthProvider.GITHUB);
        String ref = normalizeRef(repositoryRef, accessToken);
        Integer commitCount = pageCount(
                repositoryRef,
                accessToken,
                "commits",
                Map.of("sha", ref)
        );
        Integer contributorCount = pageCount(
                repositoryRef,
                accessToken,
                "contributors",
                Map.of("anon", "true")
        );
        TreeStats treeStats = treeStats(repositoryRef, ref, accessToken);

        return new RepositoryAnalysisStats(
                commitCount,
                treeStats.fileCount(),
                contributorCount,
                treeStats.directories()
        );
    }

    private String normalizeRef(RepositoryRef repositoryRef, String accessToken) {
        if (StringUtils.hasText(repositoryRef.ref()) && !"HEAD".equalsIgnoreCase(repositoryRef.ref())) {
            return repositoryRef.ref();
        }

        try {
            String raw = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment("repos", repositoryRef.owner(), repositoryRef.name())
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .retrieve()
                    .body(String.class);

            JsonNode body = raw == null ? null : OBJECT_MAPPER.readTree(raw);
            String defaultBranch = body == null ? "" : body.path("default_branch").asText("");
            return StringUtils.hasText(defaultBranch) ? defaultBranch : "HEAD";
        } catch (RestClientResponseException exception) {
            throw toGitHubException(exception, "GitHub repository metadata request failed");
        } catch (RestClientException exception) {
            throw new GitHubRepositoryFileClientException(
                    "GitHub repository metadata request failed",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    GitHubApiRateLimit.empty(),
                    exception
            );
        } catch (Exception exception) {
            throw new GitHubRepositoryFileClientException(
                    "GitHub repository metadata parse failed",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    GitHubApiRateLimit.empty(),
                    exception
            );
        }
    }

    private Integer pageCount(
            RepositoryRef repositoryRef,
            String accessToken,
            String resource,
            Map<String, String> params
    ) {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .pathSegment("repos", repositoryRef.owner(), repositoryRef.name(), resource)
                                .queryParam("per_page", 1);
                        params.forEach(builder::queryParam);
                        return builder.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .retrieve()
                    .toEntity(String.class);

            String link = response.getHeaders().getFirst(HttpHeaders.LINK);
            Integer countFromLink = lastPageFromLink(link);
            if (countFromLink != null) {
                return countFromLink;
            }

            String raw = response.getBody();
            JsonNode body = raw == null ? null : OBJECT_MAPPER.readTree(raw);
            return body != null && body.isArray() ? body.size() : 0;
        } catch (RestClientResponseException exception) {
            throw toGitHubException(exception, "GitHub repository " + resource + " request failed");
        } catch (RestClientException exception) {
            throw new GitHubRepositoryFileClientException(
                    "GitHub repository " + resource + " request failed",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    GitHubApiRateLimit.empty(),
                    exception
            );
        } catch (Exception exception) {
            throw new GitHubRepositoryFileClientException(
                    "GitHub repository " + resource + " parse failed",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    GitHubApiRateLimit.empty(),
                    exception
            );
        }
    }

    private TreeStats treeStats(RepositoryRef repositoryRef, String ref, String accessToken) {
        try {
            String raw = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment("repos", repositoryRef.owner(), repositoryRef.name(), "git", "trees", ref)
                            .queryParam("recursive", 1)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .retrieve()
                    .body(String.class);

            JsonNode body = raw == null ? null : OBJECT_MAPPER.readTree(raw);
            JsonNode tree = body == null ? null : body.path("tree");
            if (tree == null || !tree.isArray()) {
                return new TreeStats(0, List.of());
            }

            Map<String, Integer> directoryCounts = new LinkedHashMap<>();
            int fileCount = 0;
            for (JsonNode item : tree) {
                if (!"blob".equals(item.path("type").asText())) {
                    continue;
                }
                fileCount += 1;
                String path = item.path("path").asText("");
                String directory = topLevelDirectory(path);
                if (StringUtils.hasText(directory)) {
                    directoryCounts.merge(directory, 1, Integer::sum);
                }
            }

            int totalFiles = Math.max(fileCount, 1);
            List<RepositoryAnalysisStats.DirectoryStat> directories = directoryCounts.entrySet()
                    .stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                    .limit(8)
                    .map(entry -> new RepositoryAnalysisStats.DirectoryStat(
                            entry.getKey(),
                            entry.getValue(),
                            Math.max(1, Math.round((entry.getValue() * 100f) / totalFiles))
                    ))
                    .toList();

            return new TreeStats(fileCount, directories);
        } catch (RestClientResponseException exception) {
            throw toGitHubException(exception, "GitHub repository tree request failed");
        } catch (RestClientException exception) {
            throw new GitHubRepositoryFileClientException(
                    "GitHub repository tree request failed",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    GitHubApiRateLimit.empty(),
                    exception
            );
        } catch (Exception exception) {
            throw new GitHubRepositoryFileClientException(
                    "GitHub repository tree parse failed",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    GitHubApiRateLimit.empty(),
                    exception
            );
        }
    }

    private String topLevelDirectory(String path) {
        if (!StringUtils.hasText(path) || !path.contains("/")) {
            return "";
        }
        return path.substring(0, path.indexOf('/'));
    }

    private Integer lastPageFromLink(String link) {
        if (!StringUtils.hasText(link)) {
            return null;
        }

        for (String part : link.split(",")) {
            if (!part.contains("rel=\"last\"")) {
                continue;
            }
            // avoid matching "per_page=" — look for [?&]page=
            int pageIndex = -1;
            for (String marker : new String[]{"&page=", "?page="}) {
                int idx = part.indexOf(marker);
                if (idx >= 0) {
                    pageIndex = idx + marker.length() - "page=".length();
                    break;
                }
            }
            if (pageIndex < 0) {
                return null;
            }
            int start = pageIndex + "page=".length();
            int end = start;
            while (end < part.length() && Character.isDigit(part.charAt(end))) {
                end += 1;
            }
            if (end > start) {
                return Integer.parseInt(part.substring(start, end));
            }
        }

        return null;
    }

    private GitHubRepositoryFileClientException toGitHubException(
            RestClientResponseException exception,
            String message
    ) {
        return new GitHubRepositoryFileClientException(
                message,
                exception.getStatusCode(),
                GitHubApiRateLimit.from(exception.getResponseHeaders()),
                exception
        );
    }

    private record TreeStats(
            int fileCount,
            List<RepositoryAnalysisStats.DirectoryStat> directories
    ) {
    }
}
