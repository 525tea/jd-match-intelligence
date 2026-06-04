package jobflow.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobCrawlingMetadataTest {

    @Test
    @DisplayName("공고 수집 metadata를 갱신한다")
    void updateCrawlingMetadata() {
        Job job = Job.create(
                "ZIGHANG",
                "job-1",
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 개발자를 채용합니다.",
                "https://zighang.com/jobs/job-1",
                JobRole.BACKEND,
                "백엔드",
                CareerLevel.ANY,
                0,
                3,
                null,
                EmploymentType.FULL_TIME,
                null,
                null,
                "KR",
                "서울",
                null,
                RemoteType.HYBRID,
                null,
                null,
                "KRW",
                false,
                null,
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 30, 23, 59)
        );

        LocalDateTime collectedAt = LocalDateTime.of(2026, 6, 3, 10, 0);
        LocalDateTime lastSeenAt = LocalDateTime.of(2026, 6, 3, 11, 0);
        LocalDateTime sourceUpdatedAt = LocalDateTime.of(2026, 6, 2, 9, 0);

        job.updateCrawlingMetadata(
                "jobflow|backend developer|seoul",
                "https://zighang.com/jobs/job-1?utm_source=jobflow",
                collectedAt,
                lastSeenAt,
                sourceUpdatedAt,
                "{\"title\":\"백엔드 개발자\"}",
                "JobFlowCrawler/0.1"
        );

        assertThat(job.getOriginalUrl()).isEqualTo("https://zighang.com/jobs/job-1?utm_source=jobflow");
        assertThat(job.getCollectedAt()).isEqualTo(collectedAt);
        assertThat(job.getLastSeenAt()).isEqualTo(lastSeenAt);
        assertThat(job.getSourceUpdatedAt()).isEqualTo(sourceUpdatedAt);
        assertThat(job.getRawData()).isEqualTo("{\"title\":\"백엔드 개발자\"}");
        assertThat(job.getCrawlerVersion()).isEqualTo("JobFlowCrawler/0.1");
        assertThat(job.getCanonicalFingerprint()).isEqualTo("jobflow|backend developer|seoul");
    }
}
