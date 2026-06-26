package jobflow.gateway.security;

import jobflow.gateway.config.InstantIsoSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

import java.time.Instant;

public record GatewaySecurityEvent(
        @JsonSerialize(using = InstantIsoSerializer.class)
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
