package jobflow.domain.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.notification.mailgun")
public record MailgunProperties(
        String baseUrl,
        String domain,
        String apiKey,
        String from
) {

    private static final String DEFAULT_BASE_URL = "https://api.mailgun.net";
    private static final String DEFAULT_FROM = "no-reply@localhost";

    public MailgunProperties {
        baseUrl = StringUtils.hasText(baseUrl) ? stripTrailingSlash(baseUrl) : DEFAULT_BASE_URL;
        from = StringUtils.hasText(from) ? from : DEFAULT_FROM;
    }

    boolean isConfigured() {
        return StringUtils.hasText(domain) && StringUtils.hasText(apiKey) && StringUtils.hasText(from);
    }

    private static String stripTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
