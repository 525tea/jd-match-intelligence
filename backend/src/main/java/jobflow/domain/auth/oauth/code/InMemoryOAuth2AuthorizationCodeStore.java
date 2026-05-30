package jobflow.domain.auth.oauth.code;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class InMemoryOAuth2AuthorizationCodeStore implements OAuth2AuthorizationCodeStore {

    private static final Duration CODE_TTL = Duration.ofSeconds(30);

    private final Map<String, OAuth2AuthorizationCode> store = new ConcurrentHashMap<>();

    @Override
    public OAuth2AuthorizationCode save(Long userId) {
        String code = UUID.randomUUID().toString();
        OAuth2AuthorizationCode authorizationCode = new OAuth2AuthorizationCode(
                code,
                userId,
                Instant.now().plus(CODE_TTL)
        );

        store.put(code, authorizationCode);

        return authorizationCode;
    }

    @Override
    public OAuth2AuthorizationCode consume(String code) {
        OAuth2AuthorizationCode authorizationCode = store.remove(code);

        if (authorizationCode == null || authorizationCode.isExpired()) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_CODE_INVALID);
        }

        return authorizationCode;
    }
}
