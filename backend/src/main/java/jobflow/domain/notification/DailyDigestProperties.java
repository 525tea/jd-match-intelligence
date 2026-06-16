package jobflow.domain.notification;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification.daily-digest")
public record DailyDigestProperties(
        Duration idempotencyTtl,
        int maxAttempts
) {

    private static final Duration DEFAULT_IDEMPOTENCY_TTL = Duration.ofHours(25);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    public DailyDigestProperties {
        idempotencyTtl = idempotencyTtl == null ? DEFAULT_IDEMPOTENCY_TTL : idempotencyTtl;
        maxAttempts = maxAttempts <= 0 ? DEFAULT_MAX_ATTEMPTS : maxAttempts;
    }
}
