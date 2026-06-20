package jobflow.domain.auth.oauth.code;

import jobflow.global.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisOAuth2AuthorizationCodeStoreTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final RedisOAuth2AuthorizationCodeStore store = new RedisOAuth2AuthorizationCodeStore(redisTemplate);

    @Test
    @DisplayName("OAuth2 authorization code를 Redis에 5분 TTL로 저장한다")
    void saveCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        OAuth2AuthorizationCode authorizationCode = store.save(1L);

        assertThat(authorizationCode.code()).isNotBlank();
        assertThat(authorizationCode.userId()).isEqualTo(1L);
        verify(valueOperations).set(
                eq("oauth2:code:" + authorizationCode.code()),
                any(String.class),
                eq(Duration.ofMinutes(5))
        );
    }

    @Test
    @DisplayName("OAuth2 authorization code를 Redis에서 atomic consume 한다")
    void consumeCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("oauth2:code:test-code"))
                .thenReturn("1|4102444800000");

        OAuth2AuthorizationCode authorizationCode = store.consume("test-code");

        assertThat(authorizationCode.code()).isEqualTo("test-code");
        assertThat(authorizationCode.userId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("없는 OAuth2 authorization code는 invalid code로 실패한다")
    void consumeMissingCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("oauth2:code:missing-code"))
                .thenReturn(null);

        assertThatThrownBy(() -> store.consume("missing-code"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("만료된 OAuth2 authorization code는 invalid code로 실패한다")
    void consumeExpiredCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("oauth2:code:expired-code"))
                .thenReturn("1|1");

        assertThatThrownBy(() -> store.consume("expired-code"))
                .isInstanceOf(BusinessException.class);
    }
}
