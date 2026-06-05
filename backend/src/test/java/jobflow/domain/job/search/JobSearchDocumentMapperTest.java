package jobflow.domain.job.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.RemoteType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobSearchDocumentMapperTest {

    private final JobSearchDocumentMapper mapper = new JobSearchDocumentMapper();

    @Test
    @DisplayName("Job을 Elasticsearch 검색 document로 변환한다")
    void toDocument() {
        Job job = Job.create(
                "ZIGHANG",
                "job-1",
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot와 Kubernetes 기반 서비스를 개발합니다.",
                "https://example.com/jobs/job-1",
                JobRole.BACKEND,
                "백엔드",
                CareerLevel.JUNIOR,
                0,
                3,
                null,
                EmploymentType.FULL_TIME,
                null,
                "IT",
                "KR",
                "서울",
                "강남",
                RemoteType.HYBRID,
                null,
                null,
                "KRW",
                false,
                null,
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        );
        job.updateCrawlingMetadata(
                "jobflow|backend developer|seoul",
                "https://example.com/jobs/job-1",
                LocalDateTime.of(2026, 6, 5, 9, 0),
                LocalDateTime.of(2026, 6, 5, 9, 0),
                null,
                "{\"title\":\"백엔드 개발자\"}",
                "JobFlowCrawler/0.1"
        );

        JobSearchDocument document = mapper.toDocument(job);

        assertThat(document.source()).isEqualTo("ZIGHANG");
        assertThat(document.externalId()).isEqualTo("job-1");
        assertThat(document.canonicalFingerprint()).isEqualTo("jobflow|backend developer|seoul");
        assertThat(document.title()).isEqualTo("백엔드 개발자");
        assertThat(document.companyName()).isEqualTo("JobFlow");
        assertThat(document.description()).contains("Kubernetes");
        assertThat(document.role()).isEqualTo("BACKEND");
        assertThat(document.roleDetail()).isEqualTo("백엔드");
        assertThat(document.careerLevel()).isEqualTo("JUNIOR");
        assertThat(document.employmentType()).isEqualTo("FULL_TIME");
        assertThat(document.industry()).isEqualTo("IT");
        assertThat(document.locationCountry()).isEqualTo("KR");
        assertThat(document.locationRegion()).isEqualTo("서울");
        assertThat(document.locationCity()).isEqualTo("강남");
        assertThat(document.remoteType()).isEqualTo("HYBRID");
        assertThat(document.deadlineAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 23, 59));
    }
}
