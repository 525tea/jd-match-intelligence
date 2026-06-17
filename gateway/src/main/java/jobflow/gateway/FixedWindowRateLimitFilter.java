package jobflow.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class FixedWindowRateLimitFilter implements GatewayFilter {

    private static final DateTimeFormatter WINDOW_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHHmm")
            .withZone(ZoneOffset.UTC);
    private static final Duration WINDOW_TTL = Duration.ofSeconds(70);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final Clock clock;
    private final int requestsPerMinute;

    @Autowired
    public FixedWindowRateLimitFilter(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${gateway.rate-limit.requests-per-minute:100}") int requestsPerMinute
    ) {
        this(redisTemplate, Clock.systemUTC(), requestsPerMinute);
    }

    FixedWindowRateLimitFilter(
            ReactiveStringRedisTemplate redisTemplate,
            Clock clock,
            int requestsPerMinute
    ) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String key = rateLimitKey(exchange);

        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> redisTemplate.expire(key, WINDOW_TTL)
                        .thenReturn(count)
                )
                .flatMap(count -> {
                    exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
                    exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(Math.max(0, requestsPerMinute - count)));

                    if (count > requestsPerMinute) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }

                    return chain.filter(exchange);
                });
    }

    private String rateLimitKey(ServerWebExchange exchange) {
        String clientKey = clientKey(exchange);
        String window = WINDOW_FORMATTER.format(Instant.now(clock));

        return "gateway:rate-limit:" + clientKey + ":" + window;
    }

    private String clientKey(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return sanitize(forwardedFor.split(",")[0].trim());
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }

        return sanitize(remoteAddress.getAddress().getHostAddress());
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9.:_-]", "_");
    }
}
