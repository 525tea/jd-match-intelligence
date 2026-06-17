package jobflow.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class DailyDigestIdempotencyServiceTest {

    private final StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
    private final DailyDigestProperties properties = new DailyDigestProperties(Duration.ofHours(25), 3, null);
    private final DailyDigestIdempotencyService service = new DailyDigestIdempotencyService(
            redisTemplate,
            properties
    );

    @Test
    @DisplayName("Daily Digest idempotency key는 userId와 digest date를 포함한다")
    void key() {
        assertThat(service.key(10L, LocalDate.of(2026, 6, 17)))
                .isEqualTo("daily_digest:10:DAILY_DIGEST:date:2026-06-17");
    }

    @Test
    @DisplayName("Redis SETNX 성공 시 lock 획득 성공으로 처리한다")
    void acquire() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(
                "daily_digest:10:DAILY_DIGEST:date:2026-06-17",
                "1",
                Duration.ofHours(25)
        )).willReturn(true);

        boolean acquired = service.acquire(10L, LocalDate.of(2026, 6, 17));

        assertThat(acquired).isTrue();
        verify(valueOperations).setIfAbsent(
                "daily_digest:10:DAILY_DIGEST:date:2026-06-17",
                "1",
                Duration.ofHours(25)
        );
    }

    @Test
    @DisplayName("Redis SETNX 실패 시 lock 획득 실패로 처리한다")
    void acquireReturnsFalseWhenRedisReturnsFalse() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(
                "daily_digest:10:DAILY_DIGEST:date:2026-06-17",
                "1",
                Duration.ofHours(25)
        )).willReturn(false);

        boolean acquired = service.acquire(10L, LocalDate.of(2026, 6, 17));

        assertThat(acquired).isFalse();
    }

    @Test
    @DisplayName("실패한 발송은 Redis lock을 해제할 수 있다")
    void release() {
        service.release(10L, LocalDate.of(2026, 6, 17));

        verify(redisTemplate).delete("daily_digest:10:DAILY_DIGEST:date:2026-06-17");
    }
}
