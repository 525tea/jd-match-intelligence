package jobflow.domain.project;

import jobflow.global.cache.CacheEvictionService;
import jobflow.global.cache.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ProjectAnalysisCacheEvictionListener {

    private final CacheEvictionService cacheEvictionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void evictProjectAnalysisCaches(ProjectAnalysisUpdatedEvent event) {
        evictProjectInventory(event.userId(), event.userProjectId());
        evictProjectDerivedCaches(event.userId(), event.userProjectId());
    }

    private void evictProjectInventory(Long userId, Long userProjectId) {
        String key = ProjectInventoryService.projectInventoryCacheKey(userId, userProjectId);
        cacheEvictionService.evict(CacheNames.PROJECT_SKILL_INVENTORY, key);
        cacheEvictionService.evict(CacheNames.PROJECT_EXPERIENCE_TAG_INVENTORY, key);
    }

    private void evictProjectDerivedCaches(Long userId, Long userProjectId) {
        String keyPrefix = projectScopedCacheKeyPrefix(userId, userProjectId);
        cacheEvictionService.evictByKeyPrefix(CacheNames.GAP_ANALYSIS, keyPrefix);
        cacheEvictionService.evictByKeyPrefix(CacheNames.JD_MATCH, keyPrefix);
        cacheEvictionService.evictByKeyPrefix(CacheNames.JOB_RECOMMENDATION, keyPrefix);
    }

    static String projectScopedCacheKeyPrefix(Long userId, Long userProjectId) {
        return "userId=" + userId + ":projectId=" + userProjectId + ":";
    }
}
