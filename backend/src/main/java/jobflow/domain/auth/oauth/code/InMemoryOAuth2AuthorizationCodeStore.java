package jobflow.domain.auth.oauth.code;

import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
        OAuth2AuthorizationCode authorizationCode = store.get(code);

        if (authorizationCode == null) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_CODE_INVALID);
        }

        if (authorizationCode.isExpired()) {
            store.remove(code);
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_CODE_INVALID);
        }

        store.remove(code);

        return authorizationCode;
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictExpiredCodes() {
        store.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
