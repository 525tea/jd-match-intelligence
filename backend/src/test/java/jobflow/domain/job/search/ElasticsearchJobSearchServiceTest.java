package jobflow.domain.job.search;

import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ElasticsearchJobSearchServiceTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private JobSearchQueryExpansionService jobSearchQueryExpansionService;

    private final JobSearchIntentParser jobSearchIntentParser = new JobSearchIntentParser();

    @Mock
    private SearchHits<JobSearchDocument> searchHits;

    @Mock
    private SearchHit<JobSearchDocument> searchHit;

    private final JobSearchProperties jobSearchProperties = new JobSearchProperties(
            "http://localhost:9200",
            "jobflow-jobs",
            "jobflow-jobs-v1",
            false,
            true
    );

    @Test
    @DisplayName("키워드로 Elasticsearch 공고 document를 검색한다")
    void search() {
        ElasticsearchJobSearchService service = new ElasticsearchJobSearchService(
                elasticsearchOperations,
                jobSearchProperties,
                jobSearchQueryExpansionService,
                jobSearchIntentParser
        );
        JobSearchDocument document = document();

        given(jobSearchQueryExpansionService.expand("QueryDSL"))
                .willReturn(List.of());
        given(elasticsearchOperations.search(
                any(NativeQuery.class),
                eq(JobSearchDocument.class),
                eq(IndexCoordinates.of("jobflow-jobs"))
        )).willReturn(searchHits);
        given(searchHits.stream()).willReturn(Stream.of(searchHit));
        given(searchHit.getContent()).willReturn(document);
        given(searchHit.getScore()).willReturn(3.5f);

        List<JobSearchResult> results = service.search(" QueryDSL ", 10);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().id()).isEqualTo(1L);
        assertThat(results.getFirst().title()).isEqualTo("백엔드 개발자");
        assertThat(results.getFirst().score()).isEqualTo(3.5);

        ArgumentCaptor<NativeQuery> queryCaptor = ArgumentCaptor.forClass(NativeQuery.class);

        verify(elasticsearchOperations).search(
                queryCaptor.capture(),
                eq(JobSearchDocument.class),
                eq(IndexCoordinates.of("jobflow-jobs"))
        );
        verify(jobSearchQueryExpansionService).expand("QueryDSL");

        Query capturedQuery = queryCaptor.getValue().getQuery();

        assertThat(capturedQuery.isFunctionScore()).isTrue();
        assertThat(capturedQuery.functionScore().query().isMultiMatch()).isTrue();
        assertThat(capturedQuery.functionScore().query().multiMatch().query()).isEqualTo("QueryDSL");
        assertThat(capturedQuery.functionScore().query().multiMatch().operator()).isNull();
        assertThat(capturedQuery.functionScore().functions()).hasSize(2);
        assertThat(capturedQuery.functionScore().functions().getFirst().filter().exists().field())
                .isEqualTo("deadlineAt");
        assertThat(capturedQuery.functionScore().functions().get(1).filter()).isNull();
        assertThat(capturedQuery.functionScore().scoreMode()).isEqualTo(FunctionScoreMode.Sum);
        assertThat(capturedQuery.functionScore().boostMode()).isEqualTo(FunctionBoostMode.Sum);
    }

    @Test
    @DisplayName("co-occurrence 확장 후보가 있으면 원 검색어는 must, 확장 검색어는 낮은 boost should로 추가한다")
    void searchWithCooccurrenceExpansion() {
        ElasticsearchJobSearchService service = new ElasticsearchJobSearchService(
                elasticsearchOperations,
                jobSearchProperties,
                jobSearchQueryExpansionService,
                jobSearchIntentParser
        );
        JobSearchDocument document = document();

        given(jobSearchQueryExpansionService.expand("Redis"))
                .willReturn(List.of("Spring Boot", "Kafka"));
        given(elasticsearchOperations.search(
                any(NativeQuery.class),
                eq(JobSearchDocument.class),
                eq(IndexCoordinates.of("jobflow-jobs"))
        )).willReturn(searchHits);
        given(searchHits.stream()).willReturn(Stream.of(searchHit));
        given(searchHit.getContent()).willReturn(document);
        given(searchHit.getScore()).willReturn(2.5f);

        List<JobSearchResult> results = service.search(" Redis ", 10);

        assertThat(results).hasSize(1);

        ArgumentCaptor<NativeQuery> queryCaptor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(
                queryCaptor.capture(),
                eq(JobSearchDocument.class),
                eq(IndexCoordinates.of("jobflow-jobs"))
        );

        Query searchQuery = queryCaptor.getValue().getQuery().functionScore().query();

        assertThat(searchQuery.isBool()).isTrue();
        assertThat(searchQuery.bool().must()).hasSize(1);
        assertThat(searchQuery.bool().must().getFirst().multiMatch().query()).isEqualTo("Redis");
        assertThat(searchQuery.bool().must().getFirst().multiMatch().operator()).isNull();
        assertThat(searchQuery.bool().should()).hasSize(2);
        assertThat(searchQuery.bool().should())
                .extracting(query -> query.multiMatch().query())
                .containsExactly("Spring Boot", "Kafka");
        assertThat(searchQuery.bool().should())
                .extracting(query -> query.multiMatch().operator())
                .containsExactly(Operator.And, Operator.And);
        assertThat(searchQuery.bool().should())
                .extracting(query -> query.multiMatch().boost())
                .containsExactly(0.05f, 0.05f);
        assertThat(searchQuery.bool().minimumShouldMatch()).isEqualTo("0");
    }

    @Test
    @DisplayName("deadlineAt이 있는 공고만 마감 임박 boost 대상이 된다")
    void searchAppliesDeadlineBoostOnlyWhenDeadlineExists() {
        ElasticsearchJobSearchService service = new ElasticsearchJobSearchService(
                elasticsearchOperations,
                jobSearchProperties,
                jobSearchQueryExpansionService,
                jobSearchIntentParser
        );
        JobSearchDocument document = document();

        given(jobSearchQueryExpansionService.expand("WANTED"))
                .willReturn(List.of());
        given(elasticsearchOperations.search(
                any(NativeQuery.class),
                eq(JobSearchDocument.class),
                eq(IndexCoordinates.of("jobflow-jobs"))
        )).willReturn(searchHits);
        given(searchHits.stream()).willReturn(Stream.of(searchHit));
        given(searchHit.getContent()).willReturn(document);
        given(searchHit.getScore()).willReturn(1.5f);

        List<JobSearchResult> results = service.search(" WANTED ", 10);

        assertThat(results).hasSize(1);

        ArgumentCaptor<NativeQuery> queryCaptor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(
                queryCaptor.capture(),
                eq(JobSearchDocument.class),
                eq(IndexCoordinates.of("jobflow-jobs"))
        );

        Query capturedQuery = queryCaptor.getValue().getQuery();

        assertThat(capturedQuery.functionScore().functions()).hasSize(2);
        assertThat(capturedQuery.functionScore().functions().getFirst().filter().exists().field())
                .isEqualTo("deadlineAt");
        assertThat(capturedQuery.functionScore().functions().getFirst().gauss().date().field())
                .isEqualTo("deadlineAt");
    }

    @Test
    @DisplayName("query expansion이 꺼져 있으면 co-occurrence 후보를 조회하지 않는다")
    void searchWithoutQueryExpansion() {
        JobSearchProperties disabledExpansionProperties = new JobSearchProperties(
                "http://localhost:9200",
                "jobflow-jobs",
                "jobflow-jobs-v1",
                false,
                false
        );
        ElasticsearchJobSearchService service = new ElasticsearchJobSearchService(
                elasticsearchOperations,
                disabledExpansionProperties,
                jobSearchQueryExpansionService,
                jobSearchIntentParser
        );
        JobSearchDocument document = document();

        given(elasticsearchOperations.search(
                any(NativeQuery.class),
                eq(JobSearchDocument.class),
                eq(IndexCoordinates.of("jobflow-jobs"))
        )).willReturn(searchHits);
        given(searchHits.stream()).willReturn(Stream.of(searchHit));
        given(searchHit.getContent()).willReturn(document);
        given(searchHit.getScore()).willReturn(2.5f);

        List<JobSearchResult> results = service.search(" Redis ", 10);

        assertThat(results).hasSize(1);

        ArgumentCaptor<NativeQuery> queryCaptor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(
                queryCaptor.capture(),
                eq(JobSearchDocument.class),
                eq(IndexCoordinates.of("jobflow-jobs"))
        );
        verify(jobSearchQueryExpansionService, never()).expand(any());

        Query searchQuery = queryCaptor.getValue().getQuery().functionScore().query();

        assertThat(searchQuery.isMultiMatch()).isTrue();
        assertThat(searchQuery.multiMatch().query()).isEqualTo("Redis");
        assertThat(searchQuery.multiMatch().operator()).isNull();
    }

    @Test
    @DisplayName("검색어가 비어 있으면 Elasticsearch와 query expansion을 호출하지 않는다")
    void searchBlankKeyword() {
        ElasticsearchJobSearchService service = new ElasticsearchJobSearchService(
                elasticsearchOperations,
                jobSearchProperties,
                jobSearchQueryExpansionService,
                jobSearchIntentParser
        );

        List<JobSearchResult> results = service.search(" ", 10);

        assertThat(results).isEmpty();

        verify(jobSearchQueryExpansionService, never()).expand(any());
        verify(elasticsearchOperations, never()).search(
                any(NativeQuery.class),
                eq(JobSearchDocument.class),
                any(IndexCoordinates.class)
        );
    }

    @Test
    @DisplayName("검색어에서 감지한 role, career, location 의도를 낮은 should boost로 추가한다")
    void searchWithParsedIntentBoosts() {
        ElasticsearchJobSearchService service = new ElasticsearchJobSearchService(
                elasticsearchOperations,
                jobSearchProperties,
                jobSearchQueryExpansionService,
                jobSearchIntentParser
        );
        JobSearchDocument document = document();

        given(jobSearchQueryExpansionService.expand("backend junior seoul"))
                .willReturn(List.of());
        given(elasticsearchOperations.search(
                any(NativeQuery.class),
                eq(JobSearchDocument.class),
                eq(IndexCoordinates.of("jobflow-jobs"))
        )).willReturn(searchHits);
        given(searchHits.stream()).willReturn(Stream.of(searchHit));
        given(searchHit.getContent()).willReturn(document);
        given(searchHit.getScore()).willReturn(4.5f);

        List<JobSearchResult> results = service.search(" backend junior seoul ", 10);

        assertThat(results).hasSize(1);

        ArgumentCaptor<NativeQuery> queryCaptor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(
                queryCaptor.capture(),
                eq(JobSearchDocument.class),
                eq(IndexCoordinates.of("jobflow-jobs"))
        );

        Query searchQuery = queryCaptor.getValue().getQuery().functionScore().query();

        assertThat(searchQuery.isBool()).isTrue();
        assertThat(searchQuery.bool().must()).hasSize(1);
        assertThat(searchQuery.bool().must().getFirst().multiMatch().query())
                .isEqualTo("backend junior seoul");
        assertThat(searchQuery.bool().should()).hasSize(3);
        assertThat(searchQuery.bool().should().get(0).term().field()).isEqualTo("role");
        assertThat(searchQuery.bool().should().get(0).term().value().stringValue()).isEqualTo("BACKEND");
        assertThat(searchQuery.bool().should().get(0).term().boost()).isEqualTo(2.8f);
        assertThat(searchQuery.bool().should().get(1).term().field()).isEqualTo("careerLevel");
        assertThat(searchQuery.bool().should().get(1).term().value().stringValue()).isEqualTo("JUNIOR");
        assertThat(searchQuery.bool().should().get(1).term().boost()).isEqualTo(1.8f);
        assertThat(searchQuery.bool().should().get(2).match().field()).isEqualTo("locationRegion");
        assertThat(searchQuery.bool().should().get(2).match().query().stringValue()).isEqualTo("Seoul");
        assertThat(searchQuery.bool().should().get(2).match().boost()).isEqualTo(1.4f);
        assertThat(searchQuery.bool().minimumShouldMatch()).isEqualTo("0");
    }

    private JobSearchDocument document() {
        return new JobSearchDocument(
                "1",
                "ZIGHANG",
                "job-1",
                "jobflow|backend developer|seoul",
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot와 Kubernetes 기반 서비스를 개발합니다.",
                "BACKEND",
                "백엔드",
                "JUNIOR",
                "FULL_TIME",
                "IT",
                "KR",
                "서울",
                "강남",
                "HYBRID",
                LocalDateTime.of(2026, 7, 1, 23, 59),
                LocalDateTime.of(2026, 6, 5, 9, 0),
                LocalDateTime.of(2026, 6, 5, 9, 0)
        );
    }
}
