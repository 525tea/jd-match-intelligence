package jobflow.global.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.stereotype.Component;

@Component
public class RedisCacheErrorHandler implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheErrorHandler.class);
    private static final String UNKNOWN_CACHE = "unknown";

    private final CacheFailureMetrics cacheFailureMetrics;

    public RedisCacheErrorHandler(CacheFailureMetrics cacheFailureMetrics) {
        this.cacheFailureMetrics = cacheFailureMetrics;
    }

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        recordAndLog("get", cache, exception, "serving as cache miss");
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        recordAndLog("put", cache, exception, "returning response without cache write");
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        recordAndLog("evict", cache, exception, "continuing without cache eviction");
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        recordAndLog("clear", cache, exception, "continuing without cache clear");
    }

    private void recordAndLog(String operation, Cache cache, RuntimeException exception, String behavior) {
        String cacheName = cacheName(cache);
        cacheFailureMetrics.record(operation, cacheName);
        log.warn(
                "Redis cache {} failed for cache={} exception={} behavior={}",
                operation,
                cacheName,
                exception.getClass().getSimpleName(),
                behavior
        );
    }

    private String cacheName(Cache cache) {
        if (cache == null || cache.getName() == null || cache.getName().isBlank()) {
            return UNKNOWN_CACHE;
        }
        return cache.getName();
    }
}
