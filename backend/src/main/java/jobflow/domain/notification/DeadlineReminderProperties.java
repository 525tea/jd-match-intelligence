package jobflow.domain.notification;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification.deadline-reminder")
public record DeadlineReminderProperties(
        Duration window,
        Duration idempotencyTtl,
        int maxAttempts
) {

    private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);
    private static final Duration DEFAULT_IDEMPOTENCY_TTL = Duration.ofHours(25);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    public DeadlineReminderProperties {
        window = window == null ? DEFAULT_WINDOW : window;
        idempotencyTtl = idempotencyTtl == null ? DEFAULT_IDEMPOTENCY_TTL : idempotencyTtl;
        maxAttempts = maxAttempts <= 0 ? DEFAULT_MAX_ATTEMPTS : maxAttempts;
    }
}
