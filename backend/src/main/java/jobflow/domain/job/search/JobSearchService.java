package jobflow.domain.job.search;

import java.util.List;
import jobflow.global.cache.CacheNames;
import jobflow.global.cache.JobFlowCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobSearchService {

    private final ElasticsearchJobSearchService elasticsearchJobSearchService;
    private final MySqlFullTextJobSearchService mySqlFullTextJobSearchService;
    private final JobFlowCacheProperties cacheProperties;

    @Cacheable(
            cacheNames = CacheNames.JOB_SEARCH,
            key = "#keyword.strip().toLowerCase() + ':' + #limit",
            condition = "#root.target.cacheEnabled()"
    )
    public List<JobSearchResult> search(String keyword, int limit) {
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
        if (normalizedKeyword.isBlank()) {
            return List.of();
        }

        try {
            return elasticsearchJobSearchService.search(normalizedKeyword, limit);
        } catch (RuntimeException exception) {
            log.warn(
                    "Elasticsearch search failed, falling back to MySQL FULLTEXT. keyword={}, error={}",
                    normalizedKeyword,
                    exception.getMessage()
            );

            return mySqlFullTextJobSearchService.search(normalizedKeyword, limit);
        }
    }

    public boolean cacheEnabled() {
        return cacheProperties.enabled();
    }
}
