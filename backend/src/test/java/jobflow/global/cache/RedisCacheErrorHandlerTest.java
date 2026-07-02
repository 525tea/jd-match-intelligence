package jobflow.global.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

class RedisCacheErrorHandlerTest {

    private SimpleMeterRegistry meterRegistry;
    private RedisCacheErrorHandler errorHandler;
    private Cache cache;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        errorHandler = new RedisCacheErrorHandler(new CacheFailureMetrics(meterRegistry));
        cache = mock(Cache.class);
        given(cache.getName()).willReturn(CacheNames.TREND_SKILLS);
    }

    @Test
    @DisplayName("Redis cache get 실패는 예외를 전파하지 않고 metric을 기록한다")
    void handlesCacheGetErrorAsCacheMiss() {
        assertThatNoException()
                .isThrownBy(() -> errorHandler.handleCacheGetError(
                        new IllegalStateException("redis unavailable"),
                        cache,
                        "2026-07-01:limit=20"
                ));

        assertCacheErrorCount("get", CacheNames.TREND_SKILLS, 1.0);
    }

    @Test
    @DisplayName("Redis cache put 실패는 API 응답을 막지 않고 metric을 기록한다")
    void handlesCachePutErrorWithoutBlockingResponse() {
        assertThatNoException()
                .isThrownBy(() -> errorHandler.handleCachePutError(
                        new IllegalStateException("redis unavailable"),
                        cache,
                        "2026-07-01:limit=20",
                        "response"
                ));

        assertCacheErrorCount("put", CacheNames.TREND_SKILLS, 1.0);
    }

    @Test
    @DisplayName("Redis cache evict 실패는 예외를 전파하지 않고 metric을 기록한다")
    void handlesCacheEvictErrorWithoutBlockingCaller() {
        assertThatNoException()
                .isThrownBy(() -> errorHandler.handleCacheEvictError(
                        new IllegalStateException("redis unavailable"),
                        cache,
                        "2026-07-01:limit=20"
                ));

        assertCacheErrorCount("evict", CacheNames.TREND_SKILLS, 1.0);
    }

    @Test
    @DisplayName("Redis cache clear 실패는 예외를 전파하지 않고 metric을 기록한다")
    void handlesCacheClearErrorWithoutBlockingCaller() {
        assertThatNoException()
                .isThrownBy(() -> errorHandler.handleCacheClearError(
                        new IllegalStateException("redis unavailable"),
                        cache
                ));

        assertCacheErrorCount("clear", CacheNames.TREND_SKILLS, 1.0);
    }

    @Test
    @DisplayName("Spring Cache interceptor에 Redis cache error handler를 연결한다")
    void configuresSpringCacheErrorHandler() {
        RedisCacheErrorHandlingConfig config = new RedisCacheErrorHandlingConfig(errorHandler);

        CacheErrorHandler configuredErrorHandler = config.errorHandler();

        assertThat(configuredErrorHandler).isSameAs(errorHandler);
    }

    private void assertCacheErrorCount(String operation, String cacheName, double expectedCount) {
        assertThat(meterRegistry.get("jobflow.cache.redis.error")
                .tag("operation", operation)
                .tag("cache", cacheName)
                .counter()
                .count()
        ).isEqualTo(expectedCount);
    }
}
