package jobflow.global.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CacheEvictionServiceTest {

    @Test
    @DisplayName("트랜잭션 동기화가 없으면 캐시 삭제 작업을 즉시 실행한다")
    void evictAfterCommitRunsImmediatelyWithoutTransactionSynchronization() {
        CacheEvictionService service = new CacheEvictionService(
                mock(CacheManager.class),
                mock(StringRedisTemplate.class)
        );
        AtomicInteger count = new AtomicInteger();

        service.evictAfterCommit(count::incrementAndGet);

        assertThat(count).hasValue(1);
    }

    @Test
    @DisplayName("트랜잭션 동기화가 있으면 캐시 삭제 작업을 커밋 이후 실행한다")
    void evictAfterCommitRunsAfterTransactionCommit() {
        CacheEvictionService service = new CacheEvictionService(
                mock(CacheManager.class),
                mock(StringRedisTemplate.class)
        );
        AtomicInteger count = new AtomicInteger();

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.evictAfterCommit(count::incrementAndGet);

            assertThat(count).hasValue(0);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            assertThat(count).hasValue(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("정확한 cache key를 삭제한다")
    void evictsExactCacheKey() {
        CacheManager cacheManager = new ConcurrentMapCacheManager(CacheNames.PROJECT_SKILL_INVENTORY);
        CacheEvictionService service = new CacheEvictionService(
                cacheManager,
                mock(StringRedisTemplate.class)
        );
        Cache cache = cacheManager.getCache(CacheNames.PROJECT_SKILL_INVENTORY);
        cache.put("userId=1:projectId=10", "cached-value");

        service.evict(CacheNames.PROJECT_SKILL_INVENTORY, "userId=1:projectId=10");

        assertThat(cache.get("userId=1:projectId=10")).isNull();
    }

    @Test
    @DisplayName("cache namespace 전체를 삭제한다")
    void clearsCacheNamespace() {
        CacheManager cacheManager = new ConcurrentMapCacheManager(CacheNames.GAP_ANALYSIS);
        CacheEvictionService service = new CacheEvictionService(
                cacheManager,
                mock(StringRedisTemplate.class)
        );
        Cache cache = cacheManager.getCache(CacheNames.GAP_ANALYSIS);
        cache.put("userId=1:projectId=10:roles=BACKEND:limit=10", "cached-value");

        service.clearNamespace(CacheNames.GAP_ANALYSIS);

        assertThat(cache.get("userId=1:projectId=10:roles=BACKEND:limit=10")).isNull();
    }

    @Test
    @DisplayName("Redis cache prefix 삭제 패턴은 Spring Redis cache key 형식과 맞춘다")
    void redisKeyPatternUsesSpringRedisCachePrefix() {
        assertThat(CacheEvictionService.redisKeyPattern(
                CacheNames.JD_MATCH,
                "userId=1:projectId=10:"
        )).isEqualTo("jdMatch::userId=1:projectId=10:*");
    }

    @Test
    @DisplayName("prefix 삭제는 Redis SCAN 결과 key를 삭제한다")
    void evictsRedisKeysByPrefix() {
        CacheManager cacheManager = mock(CacheManager.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CacheEvictionService service = new CacheEvictionService(cacheManager, redisTemplate);
        TestCursor cursor = new TestCursor(List.of(
                "jdMatch::userId=1:projectId=10:roles=BACKEND:career=MID:limit=10",
                "jdMatch::userId=1:projectId=10:roles=FULLSTACK:career=MID:limit=10"
        ));
        given(redisTemplate.scan(org.mockito.ArgumentMatchers.any())).willReturn(cursor);
        given(redisTemplate.delete(org.mockito.ArgumentMatchers.anyCollection())).willReturn(2L);

        long deletedCount = service.evictByKeyPrefix(
                CacheNames.JD_MATCH,
                "userId=1:projectId=10:"
        );

        assertThat(deletedCount).isEqualTo(2L);
        verify(redisTemplate).delete(List.of(
                "jdMatch::userId=1:projectId=10:roles=BACKEND:career=MID:limit=10",
                "jdMatch::userId=1:projectId=10:roles=FULLSTACK:career=MID:limit=10"
        ));
    }
}
