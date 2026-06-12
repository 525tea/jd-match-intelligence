package jobflow.domain.job.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobSearchProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MySqlFullTextJobSearchServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Test
    @DisplayName("MySQL FULLTEXT 검색 결과를 내부 검색 결과로 변환한다")
    void search() {
        MySqlFullTextJobSearchService service = new MySqlFullTextJobSearchService(jobRepository);
        JobSearchProjection projection = projection();

        given(jobRepository.searchOpenJobsByFullText("백엔드", 20))
                .willReturn(List.of(projection));

        List<JobSearchResult> results = service.search(" 백엔드 ", 20);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().id()).isEqualTo(1L);
        assertThat(results.getFirst().title()).isEqualTo("백엔드 개발자");
        assertThat(results.getFirst().score()).isEqualTo(0.42);
        assertThat(results.getFirst().source()).isEqualTo("WANTED");

        verify(jobRepository).searchOpenJobsByFullText("백엔드", 20);
    }

    @Test
    @DisplayName("검색어가 비어 있으면 MySQL FULLTEXT를 호출하지 않는다")
    void searchBlankKeyword() {
        MySqlFullTextJobSearchService service = new MySqlFullTextJobSearchService(jobRepository);

        List<JobSearchResult> results = service.search(" ", 20);

        assertThat(results).isEmpty();

        verify(jobRepository, never()).searchOpenJobsByFullText(" ", 20);
    }

    @Test
    @DisplayName("검색 limit은 1 이상 100 이하로 보정한다")
    void clampLimit() {
        MySqlFullTextJobSearchService service = new MySqlFullTextJobSearchService(jobRepository);
        JobSearchProjection projection = projection();

        given(jobRepository.searchOpenJobsByFullText("백엔드", 100))
                .willReturn(List.of(projection));

        List<JobSearchResult> results = service.search("백엔드", 999);

        assertThat(results).hasSize(1);

        verify(jobRepository).searchOpenJobsByFullText("백엔드", 100);
    }

    private JobSearchProjection projection() {
        return new JobSearchProjection() {
            @Override
            public Long getId() {
                return 1L;
            }

            @Override
            public String getTitle() {
                return "백엔드 개발자";
            }

            @Override
            public String getCompanyName() {
                return "JobFlow";
            }

            @Override
            public String getRole() {
                return "BACKEND";
            }

            @Override
            public String getCareerLevel() {
                return "JUNIOR";
            }

            @Override
            public String getEmploymentType() {
                return "FULL_TIME";
            }

            @Override
            public String getLocationRegion() {
                return "서울";
            }

            @Override
            public String getLocationCity() {
                return "강남";
            }

            @Override
            public String getRemoteType() {
                return "HYBRID";
            }

            @Override
            public LocalDateTime getDeadlineAt() {
                return LocalDateTime.of(2026, 7, 1, 23, 59);
            }

            @Override
            public String getStatus() {
                return "OPEN";
            }

            @Override
            public Double getScore() {
                return 0.42;
            }

            @Override
            public String getSource() {
                return "WANTED";
            }
        };
    }
}
