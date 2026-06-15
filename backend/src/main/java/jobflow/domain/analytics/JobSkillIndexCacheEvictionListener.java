package jobflow.domain.analytics;

import jobflow.global.cache.CacheEvictionService;
import jobflow.global.cache.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class JobSkillIndexCacheEvictionListener {

    private final CacheEvictionService cacheEvictionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void evictJobSkillIndexDerivedCaches(JobSkillIndexRebuiltEvent event) {
        cacheEvictionService.clearNamespace(CacheNames.GAP_ANALYSIS);
        cacheEvictionService.clearNamespace(CacheNames.JD_MATCH);
        cacheEvictionService.clearNamespace(CacheNames.JOB_RECOMMENDATION);
    }
}
