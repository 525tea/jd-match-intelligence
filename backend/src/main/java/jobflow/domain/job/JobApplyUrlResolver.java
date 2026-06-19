package jobflow.domain.job;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.stereotype.Component;

@Component
public class JobApplyUrlResolver {

    public String resolve(Job job) {
        return resolve(job.getSource(), job.getExternalId(), job.getOriginalUrl(), job.getUrl());
    }

    public String resolve(String source, String externalId, String originalUrl, String url) {
        String safeOriginalUrl = validHttpUrlOrNull(originalUrl);
        if (safeOriginalUrl != null) {
            return safeOriginalUrl;
        }

        String safeUrl = validHttpUrlOrNull(url);
        if (safeUrl != null) {
            return safeUrl;
        }

        return sourceDetailUrl(source, externalId);
    }

    private String sourceDetailUrl(String source, String externalId) {
        if (source == null || externalId == null || externalId.isBlank()) {
            return null;
        }

        String trimmedExternalId = externalId.trim();
        return switch (source.trim().toUpperCase()) {
            case "WANTED" -> "https://www.wanted.co.kr/wd/" + trimmedExternalId;
            case "JUMPIT" -> "https://jumpit.saramin.co.kr/position/" + trimmedExternalId;
            case "SARAMIN" -> "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=" + trimmedExternalId;
            default -> null;
        };
    }

    private String validHttpUrlOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null || uri.getHost().isBlank()) {
                return null;
            }

            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return null;
            }

            return trimmed;
        } catch (URISyntaxException exception) {
            return null;
        }
    }
}
