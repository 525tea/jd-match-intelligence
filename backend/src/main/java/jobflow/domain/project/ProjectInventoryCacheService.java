package jobflow.domain.project;

import jobflow.global.cache.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class ProjectInventoryCacheService {

    private final CacheManager cacheManager;

    public void evictProjectInventoryAfterCommit(Long userId, Long userProjectId) {
        Runnable eviction = () -> evictProjectInventory(userId, userProjectId);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eviction.run();
                }
            });
            return;
        }

        eviction.run();
    }

    private void evictProjectInventory(Long userId, Long userProjectId) {
        String key = ProjectInventoryService.projectInventoryCacheKey(userId, userProjectId);
        evict(CacheNames.PROJECT_SKILL_INVENTORY, key);
        evict(CacheNames.PROJECT_EXPERIENCE_TAG_INVENTORY, key);
    }

    private void evict(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }
}
