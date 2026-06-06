package jobflow.domain.job.search;

import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
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
    private SearchHits<JobSearchDocument> searchHits;

    @Mock
    private SearchHit<JobSearchDocument> searchHit;

    private final JobSearchProperties jobSearchProperties = new JobSearchProperties(
            "http://localhost:9200",
            "jobflow-jobs",
            false
    );

    @Test
    @DisplayName("키워드로 Elasticsearch 공고 document를 검색한다")
    void search() {
        ElasticsearchJobSearchService service = new ElasticsearchJobSearchService(
                elasticsearchOperations,
                jobSearchProperties
        );
        JobSearchDocument document = document();

        given(elasticsearchOperations.search(
                any(NativeQuery.class),
                eq(JobSearchDocument.class),
                eq(IndexCoordinates.of("jobflow-jobs"))
        )).willReturn(searchHits);
        given(searchHits.stream()).willReturn(Stream.of(searchHit));
        given(searchHit.getContent()).willReturn(document);

        given(searchHit.getScore()).willReturn(3.5f);

        List<JobSearchResult> results = service.search(" 백엔드 ", 10);

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

        Query capturedQuery = queryCaptor.getValue().getQuery();

        assertThat(capturedQuery.isFunctionScore()).isTrue();
        assertThat(capturedQuery.functionScore().query().isMultiMatch()).isTrue();
        assertThat(capturedQuery.functionScore().functions()).hasSize(2);
        assertThat(capturedQuery.functionScore().scoreMode()).isEqualTo(FunctionScoreMode.Sum);
        assertThat(capturedQuery.functionScore().boostMode()).isEqualTo(FunctionBoostMode.Sum);
    }

    @Test
    @DisplayName("검색어가 비어 있으면 Elasticsearch를 호출하지 않는다")
    void searchBlankKeyword() {
        ElasticsearchJobSearchService service = new ElasticsearchJobSearchService(
                elasticsearchOperations,
                jobSearchProperties
        );

        List<JobSearchResult> results = service.search(" ", 10);

        assertThat(results).isEmpty();

        verify(elasticsearchOperations, never()).search(
                any(NativeQuery.class),
                eq(JobSearchDocument.class),
                any(IndexCoordinates.class)
        );
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
