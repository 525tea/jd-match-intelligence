package jobflow.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpirationMillis
) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("JWT secret must be configured.");
        }

        int secretBytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (secretBytes < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes.");
        }
    }
}
