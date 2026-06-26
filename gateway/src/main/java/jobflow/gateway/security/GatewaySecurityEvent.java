package jobflow.gateway.security;

import java.time.Instant;

public record GatewaySecurityEvent(
        Instant timestamp,
        String service,
        String environment,
        String requestId,
        String clientIp,
        String method,
        String path,
        Integer status,
        Long latencyMs,
        String userAgent,
        SecurityEventType eventType,
        String principal,
        String rateLimitKey,
        String outcome
) {
}
