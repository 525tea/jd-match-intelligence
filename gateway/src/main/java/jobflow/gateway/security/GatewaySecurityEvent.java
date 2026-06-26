package jobflow.gateway.security;

public record GatewaySecurityEvent(
        String timestamp,
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
