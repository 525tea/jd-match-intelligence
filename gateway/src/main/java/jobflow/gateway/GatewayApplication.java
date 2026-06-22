package jobflow.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

import java.net.URI;

@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    RouteLocator backendRoutes(
            RouteLocatorBuilder builder,
            FixedWindowRateLimitFilter fixedWindowRateLimitFilter,
            @Value("${gateway.backend-url:http://localhost:8080}") String backendUrl
    ) {
        return builder.routes()
                .route("blocked-backend-actuator", route -> route
                        .path("/api/actuator/**")
                        .filters(filters -> filters.setStatus(HttpStatus.NOT_FOUND))
                        .uri("no://op")
                )
                .route("backend-api", route -> route
                        .path("/api/**")
                        .filters(filters -> filters
                                .stripPrefix(1)
                                .filter(fixedWindowRateLimitFilter)
                                .circuitBreaker(config -> config
                                        .setName("backendApiCircuitBreaker")
                                        .setFallbackUri(URI.create("forward:/fallback/backend"))
                                )
                        )
                        .uri(backendUrl)
                )
                .build();
    }
}
