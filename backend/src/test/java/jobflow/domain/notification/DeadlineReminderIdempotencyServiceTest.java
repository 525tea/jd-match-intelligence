package jobflow.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@SuppressWarnings("unchecked")
class DeadlineReminderIdempotencyServiceTest {

    private final StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
    private final DeadlineReminderProperties properties = new DeadlineReminderProperties(
            Duration.ofHours(24),
            Duration.ofHours(25),
            3
    );
    private final DeadlineReminderIdempotencyService service =
            new DeadlineReminderIdempotencyService(redisTemplate, properties);

    @Test
    @DisplayName("사용자와 공고 id로 deadline reminder SETNX key를 만든다")
    void buildsDeadlineReminderKey() {
        assertThat(service.key(10L, 20L)).isEqualTo("deadline_reminder:10:20");
    }

    @Test
    @DisplayName("SETNX 성공이면 idempotency lock 획득에 성공한다")
    void acquireReturnsTrueWhenSetIfAbsentSucceeds() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent("deadline_reminder:10:20", "1", Duration.ofHours(25)))
                .willReturn(true);

        boolean acquired = service.acquire(10L, 20L);

        assertThat(acquired).isTrue();
        verify(valueOperations).setIfAbsent("deadline_reminder:10:20", "1", Duration.ofHours(25));
    }

    @Test
    @DisplayName("SETNX 실패이면 이미 처리 중인 알림으로 보고 false를 반환한다")
    void acquireReturnsFalseWhenSetIfAbsentFails() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent("deadline_reminder:10:20", "1", Duration.ofHours(25)))
                .willReturn(false);

        boolean acquired = service.acquire(10L, 20L);

        assertThat(acquired).isFalse();
    }

    @Test
    @DisplayName("Redis 응답이 null이면 lock 획득 실패로 처리한다")
    void acquireReturnsFalseWhenRedisReturnsNull() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent("deadline_reminder:10:20", "1", Duration.ofHours(25)))
                .willReturn(null);

        boolean acquired = service.acquire(10L, 20L);

        assertThat(acquired).isFalse();
    }
}
