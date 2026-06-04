package jobflow.domain.outbox.payload;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobStatus;
import jobflow.domain.job.RemoteType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JobOutboxPayloadTest {

    @Test
    @DisplayName("Job outbox payload에 수집 식별자를 포함한다")
    void from() {
        Job job = createJob();
        ReflectionTestUtils.setField(job, "id", 1L);
        job.updateCrawlingMetadata(
                "canonical-fingerprint",
                "https://example.com/jobs/backend-1?utm=test",
                LocalDateTime.of(2026, 6, 4, 10, 0),
                LocalDateTime.of(2026, 6, 4, 10, 5),
                null,
                """
                        {"source":"JUMPIT","externalId":"backend-1"}
                        """,
                "test-parser-0.1"
        );

        JobOutboxPayload payload = JobOutboxPayload.from(job);

        assertThat(payload.jobId()).isEqualTo(1L);
        assertThat(payload.source()).isEqualTo("JUMPIT");
        assertThat(payload.externalId()).isEqualTo("backend-1");
        assertThat(payload.canonicalFingerprint()).isEqualTo("canonical-fingerprint");
        assertThat(payload.title()).isEqualTo("Backend Engineer");
        assertThat(payload.companyName()).isEqualTo("JobFlow Labs");
        assertThat(payload.status()).isEqualTo(JobStatus.OPEN);
    }

    private Job createJob() {
        return Job.create(
                "JUMPIT",
                "backend-1",
                "Backend Engineer",
                "JobFlow Labs",
                "Spring Boot 기반 백엔드 개발자를 채용합니다.",
                "https://example.com/jobs/backend-1",
                JobRole.BACKEND,
                "Java/Spring",
                CareerLevel.JUNIOR,
                0,
                3,
                "학력무관",
                EmploymentType.FULL_TIME,
                "STARTUP",
                "IT",
                "KR",
                "Seoul",
                "Gangnam",
                RemoteType.HYBRID,
                null,
                null,
                "KRW",
                false,
                null,
                null,
                null
        );
    }
}
