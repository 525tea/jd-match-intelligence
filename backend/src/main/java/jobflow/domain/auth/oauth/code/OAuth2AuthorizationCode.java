package jobflow.domain.auth.oauth.code;

import java.time.Instant;

public record OAuth2AuthorizationCode(
        String code,
        Long userId,
        Instant expiresAt
) {

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
