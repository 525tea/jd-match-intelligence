package jobflow.domain.notification;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeadlineReminderIdempotencyService {

    private static final String KEY_PREFIX = "deadline_reminder";
    private static final String LOCK_VALUE = "1";

    private final StringRedisTemplate redisTemplate;
    private final DeadlineReminderProperties properties;

    public boolean acquire(Long userId, Long jobId) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key(userId, jobId), LOCK_VALUE, properties.idempotencyTtl());
        return Boolean.TRUE.equals(acquired);
    }

    public String key(Long userId, Long jobId) {
        return KEY_PREFIX + ":" + userId + ":" + jobId;
    }

    public Duration ttl() {
        return properties.idempotencyTtl();
    }
}
