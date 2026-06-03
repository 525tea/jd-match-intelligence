package jobflow.domain.job;

import jobflow.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class JobExpirationRepositoryTest {

    @Autowired
    private JobRepository jobRepository;

    @Test
    @DisplayName("OPEN 상태이고 마감 시간이 지난 공고만 조회한다")
    void findOpenJobsBeforeDeadline() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 4, 12, 0);

        Job overdueOpenJob = jobRepository.save(createJob(
                "overdue-open",
                LocalDateTime.of(2026, 6, 4, 11, 59)
        ));
        Job futureOpenJob = jobRepository.save(createJob(
                "future-open",
                LocalDateTime.of(2026, 6, 4, 12, 1)
        ));
        Job overdueClosedJob = jobRepository.save(createJob(
                "overdue-closed",
                LocalDateTime.of(2026, 6, 4, 11, 0)
        ));
        overdueClosedJob.close();

        jobRepository.flush();

        List<Job> jobs = jobRepository.findByStatusAndDeadlineAtBefore(JobStatus.OPEN, now);

        assertThat(jobs)
                .extracting(Job::getExternalId)
                .containsExactly("overdue-open");

        assertThat(jobs).doesNotContain(futureOpenJob, overdueClosedJob);
        assertThat(overdueOpenJob.getStatus()).isEqualTo(JobStatus.OPEN);
    }

    @Test
    @DisplayName("마감 시간이 없는 OPEN 공고는 만료 대상으로 조회하지 않는다")
    void ignoreOpenJobsWithoutDeadline() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 4, 12, 0);

        jobRepository.save(createJob("no-deadline", null));
        jobRepository.flush();

        List<Job> jobs = jobRepository.findByStatusAndDeadlineAtBefore(JobStatus.OPEN, now);

        assertThat(jobs).isEmpty();
    }

    private Job createJob(String externalId, LocalDateTime deadlineAt) {
        return Job.create(
                "MANUAL",
                externalId,
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 백엔드 개발자 채용",
                "https://example.com/jobs/" + externalId,
                JobRole.BACKEND,
                "Java/Spring",
                CareerLevel.JUNIOR,
                1,
                3,
                "BACHELOR",
                EmploymentType.FULL_TIME,
                "STARTUP",
                "IT",
                "KR",
                "서울",
                "강남구",
                RemoteType.HYBRID,
                40000000,
                70000000,
                "KRW",
                true,
                1,
                LocalDateTime.of(2026, 6, 1, 0, 0),
                deadlineAt
        );
    }
}
