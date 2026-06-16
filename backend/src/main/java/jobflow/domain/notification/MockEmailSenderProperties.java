package jobflow.domain.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification.mock-email")
public record MockEmailSenderProperties(
        boolean fail,
        String failureReason,
        String providerMessageIdPrefix
) {

    private static final String DEFAULT_FAILURE_REASON = "Mock email send failed";
    private static final String DEFAULT_PROVIDER_MESSAGE_ID_PREFIX = "mock-email";

    public MockEmailSenderProperties {
        if (failureReason == null || failureReason.isBlank()) {
            failureReason = DEFAULT_FAILURE_REASON;
        }
        if (providerMessageIdPrefix == null || providerMessageIdPrefix.isBlank()) {
            providerMessageIdPrefix = DEFAULT_PROVIDER_MESSAGE_ID_PREFIX;
        }
    }
}
