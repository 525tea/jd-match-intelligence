package jobflow.domain.notification;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DailyDigestIdempotencyService {

    private static final String KEY_PREFIX = "daily_digest";
    private static final String LOCK_VALUE = "1";

    private final StringRedisTemplate redisTemplate;
    private final DailyDigestProperties properties;

    public boolean acquire(Long userId, LocalDate digestDate) {
        return acquire(userId, NotificationLog.dailyDigestDeduplicationKey(digestDate));
    }

    public boolean acquire(Long userId, String deduplicationKey) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key(userId, deduplicationKey), LOCK_VALUE, properties.idempotencyTtl());
        return Boolean.TRUE.equals(acquired);
    }

    public void release(Long userId, LocalDate digestDate) {
        release(userId, NotificationLog.dailyDigestDeduplicationKey(digestDate));
    }

    public void release(Long userId, String deduplicationKey) {
        redisTemplate.delete(key(userId, deduplicationKey));
    }

    public String key(Long userId, LocalDate digestDate) {
        return key(userId, NotificationLog.dailyDigestDeduplicationKey(digestDate));
    }

    public String key(Long userId, String deduplicationKey) {
        return KEY_PREFIX + ":" + userId + ":" + deduplicationKey;
    }
}
