package jobflow.domain.analytics;

import static org.mockito.Mockito.verify;

import jobflow.global.cache.CacheEvictionService;
import jobflow.global.cache.CacheNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobSkillIndexCacheEvictionListenerTest {

    private final CacheEvictionService cacheEvictionService = org.mockito.Mockito.mock(CacheEvictionService.class);
    private final JobSkillIndexCacheEvictionListener listener =
            new JobSkillIndexCacheEvictionListener(cacheEvictionService);

    @Test
    @DisplayName("job skill index rebuild 이벤트는 job index 기반 캐시 namespace를 삭제한다")
    void evictJobSkillIndexDerivedCaches() {
        listener.evictJobSkillIndexDerivedCaches(new JobSkillIndexRebuiltEvent(10, 8));

        verify(cacheEvictionService).clearNamespace(CacheNames.GAP_ANALYSIS);
        verify(cacheEvictionService).clearNamespace(CacheNames.JD_MATCH);
        verify(cacheEvictionService).clearNamespace(CacheNames.JOB_RECOMMENDATION);
    }
}
