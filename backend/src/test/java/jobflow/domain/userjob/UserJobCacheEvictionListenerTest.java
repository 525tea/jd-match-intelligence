package jobflow.domain.userjob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import jobflow.global.cache.CacheEvictionService;
import jobflow.global.cache.CacheNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserJobCacheEvictionListenerTest {

    private final CacheEvictionService cacheEvictionService = org.mockito.Mockito.mock(CacheEvictionService.class);
    private final UserJobCacheEvictionListener listener = new UserJobCacheEvictionListener(cacheEvictionService);

    @Test
    @DisplayName("사용자 공고 행동 변경 이벤트는 해당 사용자 추천 캐시를 삭제한다")
    void evictUserJobDerivedCaches() {
        listener.evictUserJobDerivedCaches(new UserJobChangedEvent(1L, 10L, UserJobStatus.SAVED));

        verify(cacheEvictionService).evictByKeyPrefix(
                CacheNames.JOB_RECOMMENDATION,
                "userId=1:"
        );
    }

    @Test
    @DisplayName("사용자 추천 cache prefix는 user 식별자까지만 포함한다")
    void userScopedRecommendationCacheKeyPrefix() {
        assertThat(UserJobCacheEvictionListener.userScopedRecommendationCacheKeyPrefix(1L))
                .isEqualTo("userId=1:");
    }
}
