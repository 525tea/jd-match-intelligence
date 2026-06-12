package jobflow.domain.auth.oauth.token;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;
import jobflow.domain.user.AuthProvider;

public record OAuth2ProviderTokenCommand(
        Long userId,
        AuthProvider authProvider,
        String accessToken,
        String tokenType,
        Collection<String> scopes,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt
) {

    public String normalizedScopes() {
        if (scopes == null || scopes.isEmpty()) {
            return null;
        }

        return scopes.stream()
                .filter(scope -> scope != null && !scope.isBlank())
                .map(String::trim)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(" "));
    }
}
