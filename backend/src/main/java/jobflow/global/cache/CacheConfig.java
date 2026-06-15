package jobflow.global.cache;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableCaching
@EnableConfigurationProperties(JobFlowCacheProperties.class)
public class CacheConfig {

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(30);

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory redisConnectionFactory,
            JobFlowCacheProperties cacheProperties,
            ObjectMapper objectMapper
    ) {
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(cacheConfiguration(DEFAULT_CACHE_TTL, objectMapper))
                .withInitialCacheConfigurations(cacheConfigurations(cacheProperties, objectMapper))
                .transactionAware()
                .build();
    }

    Map<String, RedisCacheConfiguration> cacheConfigurations(
            JobFlowCacheProperties cacheProperties,
            ObjectMapper objectMapper
    ) {
        return Map.of(
                CacheNames.TREND_SKILLS, cacheConfiguration(cacheProperties.trendTtl(), objectMapper),
                CacheNames.TREND_SKILL_COOCCURRENCES, cacheConfiguration(cacheProperties.trendTtl(), objectMapper),
                CacheNames.TREND_SKILL_EXPERIENCE_TAGS, cacheConfiguration(cacheProperties.trendTtl(), objectMapper),
                CacheNames.TREND_MARKET, cacheConfiguration(cacheProperties.trendTtl(), objectMapper),
                CacheNames.GAP_ANALYSIS, cacheConfiguration(cacheProperties.gapAnalysisTtl(), objectMapper),
                CacheNames.JD_MATCH, cacheConfiguration(cacheProperties.jdMatchTtl(), objectMapper),
                CacheNames.PROJECT_SKILL_INVENTORY, cacheConfiguration(cacheProperties.projectInventoryTtl(), objectMapper),
                CacheNames.PROJECT_EXPERIENCE_TAG_INVENTORY, cacheConfiguration(cacheProperties.projectInventoryTtl(), objectMapper)
        );
    }

    private RedisCacheConfiguration cacheConfiguration(Duration ttl, ObjectMapper objectMapper) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(new GenericJacksonJsonRedisSerializer(objectMapper)));
    }
}
