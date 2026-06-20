package jobflow.domain.auth.oauth.code;

import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
@Profile("!test")
public class RedisOAuth2AuthorizationCodeStore implements OAuth2AuthorizationCodeStore {

    private static final Logger log = LoggerFactory.getLogger(RedisOAuth2AuthorizationCodeStore.class);

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "oauth2:code:";
    private static final String VALUE_DELIMITER = "|";

    private final StringRedisTemplate redisTemplate;

    public RedisOAuth2AuthorizationCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public OAuth2AuthorizationCode save(Long userId) {
        String code = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(CODE_TTL);
        OAuth2AuthorizationCode authorizationCode = new OAuth2AuthorizationCode(code, userId, expiresAt);

        redisTemplate.opsForValue().set(
                key(code),
                serialize(authorizationCode),
                CODE_TTL
        );

        log.info(
                "Saved OAuth2 authorization code in Redis. code={}, userId={}, ttlSeconds={}",
                mask(code),
                userId,
                CODE_TTL.toSeconds()
        );

        return authorizationCode;
    }

    @Override
    public OAuth2AuthorizationCode consume(String code) {
        String value = redisTemplate.opsForValue().getAndDelete(key(code));

        log.info("Consuming OAuth2 authorization code from Redis. code={}, found={}", mask(code), value != null);

        if (value == null) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_CODE_INVALID);
        }

        OAuth2AuthorizationCode authorizationCode = deserialize(code, value);

        if (authorizationCode.isExpired()) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_CODE_INVALID);
        }

        return authorizationCode;
    }

    private String key(String code) {
        return KEY_PREFIX + code;
    }

    private String mask(String code) {
        if (code == null || code.length() < 8) {
            return "****";
        }

        return code.substring(0, 4) + "****" + code.substring(code.length() - 4);
    }

    private String serialize(OAuth2AuthorizationCode authorizationCode) {
        return authorizationCode.userId()
                + VALUE_DELIMITER
                + authorizationCode.expiresAt().toEpochMilli();
    }

    private OAuth2AuthorizationCode deserialize(String code, String value) {
        String[] parts = value.split("\\|", -1);

        if (parts.length != 2) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_CODE_INVALID);
        }

        try {
            Long userId = Long.valueOf(parts[0]);
            Instant expiresAt = Instant.ofEpochMilli(Long.parseLong(parts[1]));
            return new OAuth2AuthorizationCode(code, userId, expiresAt);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_CODE_INVALID);
        }
    }
}
