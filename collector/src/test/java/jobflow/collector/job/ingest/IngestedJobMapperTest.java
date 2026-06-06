package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import jobflow.collector.job.CareerLevel;
import jobflow.collector.job.EmploymentType;
import jobflow.collector.job.Job;
import jobflow.collector.job.JobRole;
import jobflow.collector.job.JobStatus;
import jobflow.collector.job.RemoteType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IngestedJobMapperTest {

    private final IngestedJobMapper mapper = new IngestedJobMapper(
            new CanonicalFingerprintGenerator(),
            new JdJobRoleClassificationService()
    );

    @Test
    @DisplayName("수집된 공고를 Job entity로 변환하고 수집 metadata를 기록한다")
    void toJob() {
        IngestedJobPosting posting = createPosting();

        Job job = mapper.toJob(posting);

        assertThat(job.getSource()).isEqualTo("ZIGHANG");
        assertThat(job.getExternalId()).isEqualTo("zighang-123");
        assertThat(job.getCanonicalFingerprint()).hasSize(64);
        assertThat(job.getTitle()).isEqualTo("백엔드 개발자");
        assertThat(job.getCompanyName()).isEqualTo("JobFlow");
        assertThat(job.getDescription()).isEqualTo("Spring Boot 기반 백엔드 개발자를 채용합니다.");
        assertThat(job.getUrl()).isEqualTo("https://zighang.com/jobs/zighang-123");
        assertThat(job.getOriginalUrl()).isEqualTo("https://zighang.com/jobs/zighang-123?utm=test");
        assertThat(job.getRole()).isEqualTo(JobRole.BACKEND);
        assertThat(job.getRoleDetail()).isEqualTo("Java/Spring");
        assertThat(job.getCareerLevel()).isEqualTo(CareerLevel.JUNIOR);
        assertThat(job.getMinExperienceYears()).isZero();
        assertThat(job.getMaxExperienceYears()).isEqualTo(3);
        assertThat(job.getEmploymentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(job.getLocationCountry()).isEqualTo("KR");
        assertThat(job.getLocationRegion()).isEqualTo("Seoul");
        assertThat(job.getLocationCity()).isEqualTo("Gangnam");
        assertThat(job.getRemoteType()).isEqualTo(RemoteType.HYBRID);
        assertThat(job.getCollectedAt()).isEqualTo(posting.collectedAt());
        assertThat(job.getLastSeenAt()).isEqualTo(posting.lastSeenAt());
        assertThat(job.getSourceUpdatedAt()).isEqualTo(posting.sourceUpdatedAt());
        assertThat(job.getRawData()).contains("백엔드 개발자");
        assertThat(job.getCrawlerVersion()).isEqualTo("zighang-parser-0.1");
        assertThat(job.getStatus()).isEqualTo(JobStatus.OPEN);
    }

    @Test
    @DisplayName("수집 공고 role이 ETC면 JD 텍스트 기반으로 보정한다")
    void toJobWithClassifiedRole() {
        IngestedJobPosting posting = createPostingWithRole(JobRole.ETC);

        Job job = mapper.toJob(posting);

        assertThat(job.getRole()).isEqualTo(JobRole.BACKEND);
    }

    private IngestedJobPosting createPosting() {
        return new IngestedJobPosting(
                JobIngestionSource.ZIGHANG,
                "zighang-123",
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 기반 백엔드 개발자를 채용합니다.",
                "https://zighang.com/jobs/zighang-123?utm=test",
                "https://zighang.com/jobs/zighang-123",
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
                4000,
                7000,
                "KRW",
                true,
                1,
                LocalDateTime.of(2026, 6, 4, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59),
                LocalDateTime.of(2026, 6, 4, 10, 0),
                LocalDateTime.of(2026, 6, 4, 10, 5),
                LocalDateTime.of(2026, 6, 4, 9, 30),
                """
                        {"titleText":"백엔드 개발자","companyText":"JobFlow"}
                        """,
                "zighang-parser-0.1"
        );
    }

    private IngestedJobPosting createPostingWithRole(JobRole role) {
        return new IngestedJobPosting(
                JobIngestionSource.ZIGHANG,
                "zighang-etc-role",
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 기반 백엔드 개발자를 채용합니다.",
                "https://zighang.com/jobs/zighang-etc-role?utm=test",
                "https://zighang.com/jobs/zighang-etc-role",
                role,
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
                4000,
                7000,
                "KRW",
                true,
                1,
                LocalDateTime.of(2026, 6, 4, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59),
                LocalDateTime.of(2026, 6, 4, 10, 0),
                LocalDateTime.of(2026, 6, 4, 10, 5),
                LocalDateTime.of(2026, 6, 4, 9, 30),
                """
                        {"titleText":"백엔드 개발자","companyText":"JobFlow"}
                        """,
                "zighang-parser-0.1"
        );
    }
}
