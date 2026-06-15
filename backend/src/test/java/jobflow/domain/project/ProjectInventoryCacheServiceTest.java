package jobflow.domain.project;

import static org.assertj.core.api.Assertions.assertThat;

import jobflow.global.cache.CacheNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ProjectInventoryCacheServiceTest {

    private final ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
            CacheNames.PROJECT_SKILL_INVENTORY,
            CacheNames.PROJECT_EXPERIENCE_TAG_INVENTORY
    );

    private final ProjectInventoryCacheService service = new ProjectInventoryCacheService(cacheManager);

    @Test
    @DisplayName("트랜잭션 동기화가 없으면 프로젝트 인벤토리 캐시를 즉시 삭제한다")
    void evictProjectInventoryImmediatelyWithoutTransactionSynchronization() {
        Long userId = 1L;
        Long userProjectId = 10L;
        String key = ProjectInventoryService.projectInventoryCacheKey(userId, userProjectId);
        Cache skillCache = cacheManager.getCache(CacheNames.PROJECT_SKILL_INVENTORY);
        Cache tagCache = cacheManager.getCache(CacheNames.PROJECT_EXPERIENCE_TAG_INVENTORY);
        skillCache.put(key, "skills");
        tagCache.put(key, "tags");

        service.evictProjectInventoryAfterCommit(userId, userProjectId);

        assertThat(skillCache.get(key)).isNull();
        assertThat(tagCache.get(key)).isNull();
    }

    @Test
    @DisplayName("트랜잭션 동기화가 있으면 커밋 이후에 프로젝트 인벤토리 캐시를 삭제한다")
    void evictProjectInventoryAfterCommitWithTransactionSynchronization() {
        Long userId = 1L;
        Long userProjectId = 10L;
        String key = ProjectInventoryService.projectInventoryCacheKey(userId, userProjectId);
        Cache skillCache = cacheManager.getCache(CacheNames.PROJECT_SKILL_INVENTORY);
        Cache tagCache = cacheManager.getCache(CacheNames.PROJECT_EXPERIENCE_TAG_INVENTORY);
        skillCache.put(key, "skills");
        tagCache.put(key, "tags");

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.evictProjectInventoryAfterCommit(userId, userProjectId);

            assertThat(skillCache.get(key).get()).isEqualTo("skills");
            assertThat(tagCache.get(key).get()).isEqualTo("tags");

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            assertThat(skillCache.get(key)).isNull();
            assertThat(tagCache.get(key)).isNull();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
