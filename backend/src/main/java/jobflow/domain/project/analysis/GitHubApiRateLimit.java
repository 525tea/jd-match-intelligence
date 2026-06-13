package jobflow.domain.project.analysis;

import java.time.Instant;
import org.springframework.http.HttpHeaders;

public record GitHubApiRateLimit(
        Long limit,
        Long remaining,
        Long used,
        Instant resetAt,
        String resource
) {

    private static final String HEADER_LIMIT = "X-RateLimit-Limit";
    private static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    private static final String HEADER_USED = "X-RateLimit-Used";
    private static final String HEADER_RESET = "X-RateLimit-Reset";
    private static final String HEADER_RESOURCE = "X-RateLimit-Resource";

    public static GitHubApiRateLimit empty() {
        return new GitHubApiRateLimit(null, null, null, null, null);
    }

    public static GitHubApiRateLimit from(HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) {
            return empty();
        }

        return new GitHubApiRateLimit(
                parseLong(headers.getFirst(HEADER_LIMIT)),
                parseLong(headers.getFirst(HEADER_REMAINING)),
                parseLong(headers.getFirst(HEADER_USED)),
                parseResetAt(headers.getFirst(HEADER_RESET)),
                headers.getFirst(HEADER_RESOURCE)
        );
    }

    public boolean isExhausted() {
        return remaining != null && remaining <= 0;
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Instant parseResetAt(String value) {
        Long epochSeconds = parseLong(value);
        if (epochSeconds == null) {
            return null;
        }

        return Instant.ofEpochSecond(epochSeconds);
    }
}
