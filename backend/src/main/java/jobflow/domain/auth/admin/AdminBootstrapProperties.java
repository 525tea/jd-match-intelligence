package jobflow.domain.auth.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.admin-bootstrap")
public record AdminBootstrapProperties(
        boolean enabled,
        String email,
        String password,
        String name
) {
}
