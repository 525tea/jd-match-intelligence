package jobflow.gateway.security;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

@Component
public class GatewaySecurityEventClassifier {

    private static final Set<String> SENSITIVE_PROBE_TOKENS = Set.of(
            ".env",
            ".git",
            "wp-admin",
            "phpmyadmin",
            "server-status"
    );

    public SecurityEventType classify(String path, int status, boolean rateLimitHit) {
        if (rateLimitHit || status == 429) {
            return SecurityEventType.RATE_LIMIT_HIT;
        }
        if (isAbnormalRequest(path, status)) {
            return SecurityEventType.ABNORMAL_REQUEST;
        }
        if (status == 401 || status == 403) {
            return SecurityEventType.AUTH_FAILURE;
        }
        if (status >= 500) {
            return SecurityEventType.BACKEND_FAILURE;
        }
        return SecurityEventType.ACCESS;
    }

    private boolean isAbnormalRequest(String path, int status) {
        String normalizedPath = normalizePath(path);
        if (status == 405) {
            return true;
        }
        if (status == 404 && normalizedPath.startsWith("/api/")) {
            return true;
        }
        return containsSensitiveProbeToken(normalizedPath);
    }

    private boolean containsSensitiveProbeToken(String path) {
        if (isAllowedOperationalEndpoint(path)) {
            return false;
        }
        String lowerPath = path.toLowerCase(Locale.ROOT);
        return SENSITIVE_PROBE_TOKENS.stream().anyMatch(lowerPath::contains);
    }

    private boolean isAllowedOperationalEndpoint(String path) {
        return path.equals("/actuator/health")
                || path.equals("/actuator/prometheus");
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        return path.split("\\?", 2)[0];
    }
}
