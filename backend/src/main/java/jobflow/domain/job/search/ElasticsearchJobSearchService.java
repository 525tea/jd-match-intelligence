package jobflow.domain.job.search;

import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
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
    private static final float EXPANSION_BOOST = 0.05f;
    private static final float ROLE_INTENT_BOOST = 2.8f;
    private static final float CAREER_INTENT_BOOST = 1.8f;
    private static final float LOCATION_INTENT_BOOST = 1.4f;
    private static final String DEADLINE_AT_FIELD = "deadlineAt";
    private static final String CREATED_AT_FIELD = "createdAt";

    private final ElasticsearchOperations elasticsearchOperations;
    private final JobSearchProperties jobSearchProperties;
    private final JobSearchQueryExpansionService jobSearchQueryExpansionService;
    private final JobSearchIntentParser jobSearchIntentParser;

    public List<JobSearchResult> search(String keyword, int limit) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isBlank()) {
            return List.of();
        }

        List<String> expandedKeywords = expandKeyword(normalizedKeyword);
        JobSearchIntent intent = jobSearchIntentParser.parse(normalizedKeyword);

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.functionScore(fs -> fs
                        .query(searchQuery(normalizedKeyword, expandedKeywords, intent))
                        .functions(f -> f
                                .filter(existsQuery(DEADLINE_AT_FIELD))
                                .weight(1.4)
                                .gauss(g -> g.date(d -> d
                                        .field(DEADLINE_AT_FIELD)
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
                                        .field(CREATED_AT_FIELD)
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

    private Query searchQuery(String keyword, List<String> expandedKeywords, JobSearchIntent intent) {
        if (expandedKeywords.isEmpty() && !intent.hasAnySignal()) {
            return primaryKeywordQuery(keyword);
        }

        return Query.of(q -> q.bool(b -> {
            b.must(primaryKeywordQuery(keyword));
            intent.requiredSkillKeywords().forEach(requiredSkillKeyword ->
                    b.must(requiredSkillKeywordQuery(requiredSkillKeyword))
            );
            expandedKeywords.forEach(expandedKeyword ->
                    b.should(expansionKeywordQuery(expandedKeyword))
            );
            intent.roles().forEach(role ->
                    b.should(keywordIntentQuery("role", role.name(), ROLE_INTENT_BOOST))
            );
            intent.careerLevels().forEach(careerLevel ->
                    b.should(keywordIntentQuery("careerLevel", careerLevel.name(), CAREER_INTENT_BOOST))
            );
            intent.locationRegions().forEach(locationRegion ->
                    b.should(textIntentQuery("locationRegion", locationRegion, LOCATION_INTENT_BOOST))
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
                .operator(Operator.And)
                .boost(EXPANSION_BOOST)
                .fields(
                        "description",
                        "roleDetail^1.5",
                        "industry"
                )
        ));
    }

    private Query requiredSkillKeywordQuery(String keyword) {
        return Query.of(q -> q.multiMatch(m -> m
                .query(keyword)
                .operator(Operator.And)
                .fields(
                        "title^3",
                        "description",
                        "roleDetail^2",
                        "industry"
                )
        ));
    }

    private Query keywordIntentQuery(String field, String value, float boost) {
        return Query.of(q -> q.term(t -> t
                .field(field)
                .value(value)
                .boost(boost)
        ));
    }

    private Query textIntentQuery(String field, String value, float boost) {
        return Query.of(q -> q.match(m -> m
                .field(field)
                .query(value)
                .boost(boost)
        ));
    }

    private Query existsQuery(String field) {
        return Query.of(q -> q.exists(e -> e.field(field)));
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(limit, MAX_LIMIT);
    }
}
