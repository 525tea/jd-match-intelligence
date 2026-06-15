package jobflow.domain.project;

import jobflow.global.cache.CacheNames;
import jobflow.global.cache.CacheEvictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectInventoryCacheService {

    private final CacheEvictionService cacheEvictionService;

    public void evictProjectInventoryAfterCommit(Long userId, Long userProjectId) {
        cacheEvictionService.evictAfterCommit(() -> evictProjectInventory(userId, userProjectId));
    }

    private void evictProjectInventory(Long userId, Long userProjectId) {
        String key = ProjectInventoryService.projectInventoryCacheKey(userId, userProjectId);
        cacheEvictionService.evict(CacheNames.PROJECT_SKILL_INVENTORY, key);
        cacheEvictionService.evict(CacheNames.PROJECT_EXPERIENCE_TAG_INVENTORY, key);
    }
}
