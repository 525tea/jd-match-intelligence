package jobflow.gateway.security;

import jobflow.gateway.FixedWindowRateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.UUID;

@Component
public class GatewaySecurityEventFilter implements GlobalFilter, Ordered {

    private static final int MAX_USER_AGENT_LENGTH = 256;

    private final GatewaySecurityEventPublisher publisher;
    private final GatewaySecurityEventClassifier classifier;
    private final String service;
    private final String environment;

    public GatewaySecurityEventFilter(
            GatewaySecurityEventPublisher publisher,
            GatewaySecurityEventClassifier classifier,
            @Value("${gateway.security-events.service:gateway}") String service,
            @Value("${gateway.security-events.environment:${spring.profiles.active:local}}") String environment
    ) {
        this.publisher = publisher;
        this.classifier = classifier;
        this.service = service;
        this.environment = environment;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startedAtNanos = System.nanoTime();
        String requestId = requestId(exchange);

        return chain.filter(exchange)
                .doOnError(error -> publish(exchange, requestId, startedAtNanos, 500))
                .doFinally(signalType -> {
                    if (exchange.getAttributeOrDefault("jobflow.security-event.published-on-error", false)) {
                        return;
                    }
                    publish(exchange, requestId, startedAtNanos, responseStatus(exchange));
                });
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private void publish(ServerWebExchange exchange, String requestId, long startedAtNanos, int status) {
        if (exchange.getAttributeOrDefault("jobflow.security-event.published", false)) {
            return;
        }
        exchange.getAttributes().put("jobflow.security-event.published", true);
        if (status >= 500) {
            exchange.getAttributes().put("jobflow.security-event.published-on-error", true);
        }

        String path = exchange.getRequest().getURI().getPath();
        boolean rateLimitHit = exchange.getAttributeOrDefault(FixedWindowRateLimitFilter.RATE_LIMIT_HIT_ATTRIBUTE, false);
        String rateLimitKey = exchange.getAttribute(FixedWindowRateLimitFilter.RATE_LIMIT_KEY_ATTRIBUTE);
        long latencyMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;

        GatewaySecurityEvent event = new GatewaySecurityEvent(
                Instant.now().toString(),
                service,
                environment,
                requestId,
                clientIp(exchange),
                exchange.getRequest().getMethod().name(),
                path,
                status,
                latencyMs,
                userAgent(exchange),
                classifier.classify(path, status, rateLimitHit),
                principal(exchange),
                rateLimitKey,
                status < 400 ? "SUCCESS" : "FAILURE"
        );
        publisher.publish(event);
    }

    private String requestId(ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        if (StringUtils.hasText(requestId)) {
            return sanitize(requestId, 128);
        }
        return UUID.randomUUID().toString();
    }

    private int responseStatus(ServerWebExchange exchange) {
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        if (statusCode == null) {
            return 200;
        }
        return statusCode.value();
    }

    private String clientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return sanitize(forwardedFor.split(",")[0].trim(), 128);
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return sanitize(remoteAddress.getAddress().getHostAddress(), 128);
    }

    private String userAgent(ServerWebExchange exchange) {
        String userAgent = exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT);
        if (!StringUtils.hasText(userAgent)) {
            return "unknown";
        }
        return sanitize(userAgent, MAX_USER_AGENT_LENGTH);
    }

    private String principal(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization)) {
            return "token-present";
        }
        return "anonymous";
    }

    private String sanitize(String value, int maxLength) {
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        if (sanitized.length() <= maxLength) {
            return sanitized;
        }
        return sanitized.substring(0, maxLength);
    }
}
