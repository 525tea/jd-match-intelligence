package jobflow.domain.job.search;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ElasticsearchJobSearchService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ElasticsearchOperations elasticsearchOperations;
    private final JobSearchProperties jobSearchProperties;

    public List<JobSearchResult> search(String keyword, int limit) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isBlank()) {
            return List.of();
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.multiMatch(m -> m
                        .query(normalizedKeyword)
                        .fields(
                                "title^3",
                                "companyName^2",
                                "description",
                                "roleDetail^2",
                                "industry",
                                "locationRegion",
                                "locationCity"
                        )
                ))
                .withPageable(PageRequest.of(0, normalizeLimit(limit)))
                .build();

        return elasticsearchOperations.search(
                        query,
                        JobSearchDocument.class,
                        IndexCoordinates.of(jobSearchProperties.indexName())
                )
                .stream()
                .map(searchHit -> JobSearchResult.fromDocument(
                        searchHit.getContent(),
                        (double) searchHit.getScore()
                ))
                .toList();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(limit, MAX_LIMIT);
    }
}
