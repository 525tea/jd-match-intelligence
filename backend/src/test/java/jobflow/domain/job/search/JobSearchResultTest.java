package jobflow.domain.job.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobStatus;
import jobflow.domain.job.RemoteType;
import jobflow.domain.job.dto.JobSearchResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobSearchResultTest {

    @Test
    @DisplayName("Elasticsearch document를 검색 결과로 변환한다")
    void fromDocument() {
        JobSearchDocument document = new JobSearchDocument(
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

        JobSearchResult result = JobSearchResult.fromDocument(document, 3.5);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("백엔드 개발자");
        assertThat(result.companyName()).isEqualTo("JobFlow");
        assertThat(result.role()).isEqualTo(JobRole.BACKEND);
        assertThat(result.careerLevel()).isEqualTo(CareerLevel.JUNIOR);
        assertThat(result.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(result.locationRegion()).isEqualTo("서울");
        assertThat(result.locationCity()).isEqualTo("강남");
        assertThat(result.remoteType()).isEqualTo(RemoteType.HYBRID);
        assertThat(result.status()).isEqualTo(JobStatus.OPEN);
        assertThat(result.score()).isEqualTo(3.5);
    }

    @Test
    @DisplayName("검색 결과를 API 응답으로 변환한다")
    void toResponse() {
        JobSearchResult result = new JobSearchResult(
                1L,
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
                3.5
        );

        JobSearchResponse response = result.toResponse();

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("백엔드 개발자");
        assertThat(response.companyName()).isEqualTo("JobFlow");
        assertThat(response.score()).isEqualTo(3.5);
    }
}
