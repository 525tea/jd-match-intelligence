package jobflow.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import jobflow.global.cache.CacheEvictionService;
import jobflow.global.cache.CacheNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectAnalysisCacheEvictionListenerTest {

    private final CacheEvictionService cacheEvictionService = org.mockito.Mockito.mock(CacheEvictionService.class);
    private final ProjectAnalysisCacheEvictionListener listener =
            new ProjectAnalysisCacheEvictionListener(cacheEvictionService);

    @Test
    @DisplayName("프로젝트 분석 갱신 이벤트는 inventory와 파생 분석 캐시를 함께 삭제한다")
    void evictProjectAnalysisCaches() {
        Long userId = 1L;
        Long userProjectId = 10L;

        listener.evictProjectAnalysisCaches(new ProjectAnalysisUpdatedEvent(userId, userProjectId));

        String inventoryKey = ProjectInventoryService.projectInventoryCacheKey(userId, userProjectId);
        String keyPrefix = ProjectAnalysisCacheEvictionListener.projectScopedCacheKeyPrefix(userId, userProjectId);
        verify(cacheEvictionService).evict(CacheNames.PROJECT_SKILL_INVENTORY, inventoryKey);
        verify(cacheEvictionService).evict(CacheNames.PROJECT_EXPERIENCE_TAG_INVENTORY, inventoryKey);
        verify(cacheEvictionService).evictByKeyPrefix(CacheNames.GAP_ANALYSIS, keyPrefix);
        verify(cacheEvictionService).evictByKeyPrefix(CacheNames.JD_MATCH, keyPrefix);
        verify(cacheEvictionService).evictByKeyPrefix(CacheNames.JOB_RECOMMENDATION, keyPrefix);
    }

    @Test
    @DisplayName("프로젝트 범위 cache prefix는 user/project 식별자까지만 포함한다")
    void projectScopedCacheKeyPrefix() {
        assertThat(ProjectAnalysisCacheEvictionListener.projectScopedCacheKeyPrefix(1L, 10L))
                .isEqualTo("userId=1:projectId=10:");
    }
}
