package jobflow.collector.job.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.collector.saramin")
public record SaraminApiProperties(
        String mode,
        String apiBaseUrl,
        String accessKey,
        int defaultCount
) {

    private static final String DEFAULT_MODE = "api";
    private static final String DEFAULT_API_BASE_URL = "https://oapi.saramin.co.kr/job-search";
    private static final int DEFAULT_COUNT = 100;

    public String modeOrDefault() {
        if (mode == null || mode.isBlank()) {
            return DEFAULT_MODE;
        }

        return mode;
    }

    public String apiBaseUrlOrDefault() {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            return DEFAULT_API_BASE_URL;
        }

        return apiBaseUrl;
    }

    public String accessKeyOrThrow() {
        if (accessKey == null || accessKey.isBlank()) {
            throw new SaraminApiAccessKeyMissingException();
        }

        return accessKey;
    }

    public int defaultCountOrDefault() {
        if (defaultCount <= 0) {
            return DEFAULT_COUNT;
        }

        return defaultCount;
    }
}
