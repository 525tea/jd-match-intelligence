package jobflow.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import jobflow.global.cache.CacheFailureMetrics;
import jobflow.global.cache.CacheNames;
import jobflow.global.cache.RedisCacheErrorHandler;
import jobflow.global.cache.RedisCacheErrorHandlingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

class AnalyticsTrendCacheDegradationTest {

    @Test
    @DisplayName("트렌드 캐시 조회/저장 실패 시에도 원본 DB 조회 경로를 실행한다")
    void fallsBackToRepositoryWhenTrendCacheFails() {
        SkillTrendRepository skillTrendRepository = mock(SkillTrendRepository.class);
        given(skillTrendRepository.findByPeriodTypeAndPeriodStartOrderByTrendScoreDesc(
                AnalyticsPeriodType.MONTHLY,
                LocalDate.of(2026, 7, 1)
        )).willReturn(List.of());

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CachingTestConfig.class);
            context.registerBean(CacheManager.class, () -> new FailingCacheManager(CacheNames.TREND_SKILLS));
            context.registerBean(CacheFailureMetrics.class, () -> new CacheFailureMetrics(meterRegistry));
            context.registerBean(RedisCacheErrorHandler.class);
            context.registerBean(RedisCacheErrorHandlingConfig.class);
            context.registerBean(SkillTrendRepository.class, () -> skillTrendRepository);
            context.registerBean(SkillCooccurrenceRepository.class, () -> mock(SkillCooccurrenceRepository.class));
            context.registerBean(
                    SkillExperienceMarketRepository.class,
                    () -> mock(SkillExperienceMarketRepository.class)
            );
            context.registerBean(JobMarketStatsRepository.class, () -> mock(JobMarketStatsRepository.class));
            context.registerBean(AnalyticsTrendService.class);
            context.refresh();

            AnalyticsTrendService service = context.getBean(AnalyticsTrendService.class);

            assertThatNoException()
                    .isThrownBy(() -> assertThat(service.getSkillTrends(LocalDate.of(2026, 7, 15), 20))
                            .isEmpty());
        }

        verify(skillTrendRepository).findByPeriodTypeAndPeriodStartOrderByTrendScoreDesc(
                AnalyticsPeriodType.MONTHLY,
                LocalDate.of(2026, 7, 1)
        );
        assertCacheErrorCount(meterRegistry, "get", 1.0);
        assertCacheErrorCount(meterRegistry, "put", 1.0);
    }

    private void assertCacheErrorCount(SimpleMeterRegistry meterRegistry, String operation, double expectedCount) {
        assertThat(meterRegistry.get("jobflow.cache.redis.error")
                .tag("operation", operation)
                .tag("cache", CacheNames.TREND_SKILLS)
                .counter()
                .count()
        ).isEqualTo(expectedCount);
    }

    @Configuration
    @EnableCaching
    static class CachingTestConfig {
    }

    private record FailingCacheManager(String cacheName) implements CacheManager {

        @Override
        public Cache getCache(String name) {
            if (cacheName.equals(name)) {
                return new FailingCache(name);
            }
            return null;
        }

        @Override
        public java.util.Collection<String> getCacheNames() {
            return List.of(cacheName);
        }
    }

    private record FailingCache(String name) implements Cache {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return this;
        }

        @Override
        public ValueWrapper get(Object key) {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public void put(Object key, Object value) {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public void evict(Object key) {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public void clear() {
            throw new IllegalStateException("redis unavailable");
        }
    }
}
