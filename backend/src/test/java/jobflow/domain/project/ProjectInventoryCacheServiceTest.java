package jobflow.domain.project;

import static org.mockito.Mockito.verify;

import jobflow.global.cache.CacheEvictionService;
import jobflow.global.cache.CacheNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectInventoryCacheServiceTest {

    private final CacheEvictionService cacheEvictionService = org.mockito.Mockito.mock(CacheEvictionService.class);
    private final ProjectInventoryCacheService service = new ProjectInventoryCacheService(cacheEvictionService);

    @Test
    @DisplayName("프로젝트 인벤토리 캐시는 커밋 이후 삭제하도록 위임한다")
    void evictProjectInventoryAfterCommit() {
        Long userId = 1L;
        Long userProjectId = 10L;

        service.evictProjectInventoryAfterCommit(userId, userProjectId);

        verify(cacheEvictionService).evictAfterCommit(org.mockito.ArgumentMatchers.any(Runnable.class));
    }

    @Test
    @DisplayName("프로젝트 인벤토리 skill/tag 캐시를 같은 key로 삭제한다")
    void evictProjectInventoryUsesInventoryCacheKey() {
        Long userId = 1L;
        Long userProjectId = 10L;
        String key = ProjectInventoryService.projectInventoryCacheKey(userId, userProjectId);

        service.evictProjectInventoryAfterCommit(userId, userProjectId);

        org.mockito.ArgumentCaptor<Runnable> captor = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(cacheEvictionService).evictAfterCommit(captor.capture());
        captor.getValue().run();

        verify(cacheEvictionService).evict(CacheNames.PROJECT_SKILL_INVENTORY, key);
        verify(cacheEvictionService).evict(CacheNames.PROJECT_EXPERIENCE_TAG_INVENTORY, key);
    }
}
