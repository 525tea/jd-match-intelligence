package jobflow.domain.userjob;

import jobflow.global.cache.CacheEvictionService;
import jobflow.global.cache.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserJobCacheEvictionListener {

    private final CacheEvictionService cacheEvictionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void evictUserJobDerivedCaches(UserJobChangedEvent event) {
        cacheEvictionService.evictByKeyPrefix(
                CacheNames.JOB_RECOMMENDATION,
                userScopedRecommendationCacheKeyPrefix(event.userId())
        );
    }

    static String userScopedRecommendationCacheKeyPrefix(Long userId) {
        return "userId=" + userId + ":";
    }
}
