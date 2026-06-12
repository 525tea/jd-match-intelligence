package jobflow.domain.job.search;

import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ElasticsearchJobSearchService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final float EXPANSION_BOOST = 0.25f;

    private final ElasticsearchOperations elasticsearchOperations;
    private final JobSearchProperties jobSearchProperties;
    private final JobSearchQueryExpansionService jobSearchQueryExpansionService;

    public List<JobSearchResult> search(String keyword, int limit) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isBlank()) {
            return List.of();
        }

        List<String> expandedKeywords = expandKeyword(normalizedKeyword);

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.functionScore(fs -> fs
                        .query(searchQuery(normalizedKeyword, expandedKeywords))
                        .functions(f -> f
                                .weight(1.4)
                                .gauss(g -> g.date(d -> d
                                        .field("deadlineAt")
                                        .placement(p -> p
                                                .origin("now")
                                                .scale(Time.of(t -> t.time("14d")))
                                                .offset(Time.of(t -> t.time("1d")))
                                                .decay(0.5)
                                        )
                                ))
                        )
                        .functions(f -> f
                                .weight(1.2)
                                .gauss(g -> g.date(d -> d
                                        .field("createdAt")
                                        .placement(p -> p
                                                .origin("now")
                                                .scale(Time.of(t -> t.time("30d")))
                                                .offset(Time.of(t -> t.time("3d")))
                                                .decay(0.5)
                                        )
                                ))
                        )
                        .scoreMode(FunctionScoreMode.Sum)
                        .boostMode(FunctionBoostMode.Sum)
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

    private Query searchQuery(String keyword, List<String> expandedKeywords) {
        if (expandedKeywords.isEmpty()) {
            return primaryKeywordQuery(keyword);
        }

        return Query.of(q -> q.bool(b -> {
            b.must(primaryKeywordQuery(keyword));
            expandedKeywords.forEach(expandedKeyword ->
                    b.should(expansionKeywordQuery(expandedKeyword))
            );
            return b.minimumShouldMatch("0");
        }));
    }

    private List<String> expandKeyword(String keyword) {
        if (!jobSearchProperties.queryExpansionEnabled()) {
            return List.of();
        }

        return jobSearchQueryExpansionService.expand(keyword);
    }

    private Query primaryKeywordQuery(String keyword) {
        return Query.of(q -> q.multiMatch(m -> m
                .query(keyword)
                .fields(
                        "title^3",
                        "companyName^2",
                        "description",
                        "roleDetail^2",
                        "industry",
                        "locationRegion",
                        "locationCity"
                )
        ));
    }

    private Query expansionKeywordQuery(String keyword) {
        return Query.of(q -> q.multiMatch(m -> m
                .query(keyword)
                .boost(EXPANSION_BOOST)
                .fields(
                        "description",
                        "roleDetail^1.5",
                        "title^0.5",
                        "industry"
                )
        ));
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(limit, MAX_LIMIT);
    }
}
