package jobflow.domain.project.analysis;

import org.springframework.http.HttpStatusCode;

public class GitHubRepositoryFileClientException extends RuntimeException {

    private final HttpStatusCode statusCode;
    private final GitHubApiRateLimit rateLimit;

    public GitHubRepositoryFileClientException(
            String message,
            HttpStatusCode statusCode,
            GitHubApiRateLimit rateLimit
    ) {
        super(message);
        this.statusCode = statusCode;
        this.rateLimit = rateLimit == null ? GitHubApiRateLimit.empty() : rateLimit;
    }

    public GitHubRepositoryFileClientException(
            String message,
            HttpStatusCode statusCode,
            GitHubApiRateLimit rateLimit,
            Throwable cause
    ) {
        super(message, cause);
        this.statusCode = statusCode;
        this.rateLimit = rateLimit == null ? GitHubApiRateLimit.empty() : rateLimit;
    }

    public HttpStatusCode statusCode() {
        return statusCode;
    }

    public GitHubApiRateLimit rateLimit() {
        return rateLimit;
    }
}
