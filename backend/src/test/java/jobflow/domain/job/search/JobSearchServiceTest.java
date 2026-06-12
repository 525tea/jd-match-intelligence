package jobflow.domain.job.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobStatus;
import jobflow.domain.job.RemoteType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobSearchServiceTest {

    @Mock
    private ElasticsearchJobSearchService elasticsearchJobSearchService;

    @Mock
    private MySqlFullTextJobSearchService mySqlFullTextJobSearchService;

    @Test
    @DisplayName("Elasticsearch 검색 결과를 우선 반환한다")
    void searchWithElasticsearch() {
        JobSearchService service = new JobSearchService(
                elasticsearchJobSearchService,
                mySqlFullTextJobSearchService
        );
        JobSearchResult result = result(3.5);

        given(elasticsearchJobSearchService.search("백엔드", 20))
                .willReturn(List.of(result));

        List<JobSearchResult> results = service.search(" 백엔드 ", 20);

        assertThat(results).containsExactly(result);

        verify(elasticsearchJobSearchService).search("백엔드", 20);
        verify(mySqlFullTextJobSearchService, never()).search("백엔드", 20);
    }

    @Test
    @DisplayName("Elasticsearch 검색 실패 시 MySQL FULLTEXT로 fallback한다")
    void fallbackToMySqlFullText() {
        JobSearchService service = new JobSearchService(
                elasticsearchJobSearchService,
                mySqlFullTextJobSearchService
        );
        JobSearchResult fallbackResult = result(0.42);

        given(elasticsearchJobSearchService.search("백엔드", 20))
                .willThrow(new IllegalStateException("Elasticsearch unavailable"));
        given(mySqlFullTextJobSearchService.search("백엔드", 20))
                .willReturn(List.of(fallbackResult));

        List<JobSearchResult> results = service.search("백엔드", 20);

        assertThat(results).containsExactly(fallbackResult);

        verify(elasticsearchJobSearchService).search("백엔드", 20);
        verify(mySqlFullTextJobSearchService).search("백엔드", 20);
    }

    @Test
    @DisplayName("검색어가 비어 있으면 Elasticsearch와 MySQL을 모두 호출하지 않는다")
    void searchBlankKeyword() {
        JobSearchService service = new JobSearchService(
                elasticsearchJobSearchService,
                mySqlFullTextJobSearchService
        );

        List<JobSearchResult> results = service.search(" ", 20);

        assertThat(results).isEmpty();

        verify(elasticsearchJobSearchService, never()).search(" ", 20);
        verify(mySqlFullTextJobSearchService, never()).search(" ", 20);
    }

    private JobSearchResult result(Double score) {
        return new JobSearchResult(
                1L,
                "WANTED",
                "백엔드 개발자",
                "JobFlow",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                EmploymentType.FULL_TIME,
                "서울",
                "강남",
                RemoteType.HYBRID,
                LocalDateTime.of(2026, 7, 1, 23, 59),
                JobStatus.OPEN,
                score
        );
    }
}
