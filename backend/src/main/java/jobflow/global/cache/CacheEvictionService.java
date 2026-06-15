package jobflow.global.cache;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class CacheEvictionService {

    private static final long SCAN_COUNT = 1_000L;

    private final CacheManager cacheManager;
    private final StringRedisTemplate redisTemplate;

    public void evictAfterCommit(Runnable eviction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eviction.run();
                }
            });
            return;
        }

        eviction.run();
    }

    public void evict(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    public long evictByKeyPrefix(String cacheName, String keyPrefix) {
        return deleteRedisKeysByPattern(redisKeyPattern(cacheName, keyPrefix));
    }

    public void clearNamespace(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    static String redisKeyPattern(String cacheName, String keyPrefix) {
        return cacheName + "::" + keyPrefix + "*";
    }

    private long deleteRedisKeysByPattern(String pattern) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_COUNT)
                .build();

        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }

        if (keys.isEmpty()) {
            return 0;
        }

        Long deletedCount = redisTemplate.delete(keys);
        return deletedCount == null ? 0 : deletedCount;
    }
}
