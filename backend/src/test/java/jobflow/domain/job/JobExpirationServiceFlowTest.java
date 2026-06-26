package jobflow.domain.job;

import jobflow.domain.outbox.OutboxEvent;
import jobflow.domain.outbox.OutboxEventService;
import jobflow.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        JpaAuditingConfig.class,
        JobExpirationService.class,
        JobExpirationServiceFlowTest.FixedClockConfig.class
})
class JobExpirationServiceFlowTest {

    @Autowired
    private JobExpirationService jobExpirationService;

    @Autowired
    private JobRepository jobRepository;

    @MockitoBean
    private OutboxEventService outboxEventService;

    @Test
    @DisplayName("마감 시간이 지난 OPEN 공고만 EXPIRED로 변경하고 outbox event를 저장한다")
    void expireOnlyOverdueOpenJobs() {
        Job overdueOpenJob = jobRepository.save(createJob(
                "MANUAL",
                "overdue-open",
                LocalDateTime.of(2026, 6, 4, 11, 59)
        ));
        Job futureOpenJob = jobRepository.save(createJob(
                "MANUAL",
                "future-open",
                LocalDateTime.of(2026, 6, 4, 12, 1)
        ));
        Job overdueClosedJob = jobRepository.save(createJob(
                "MANUAL",
                "overdue-closed",
                LocalDateTime.of(2026, 6, 4, 11, 0)
        ));
        overdueClosedJob.close();

        jobRepository.flush();

        int expiredCount = jobExpirationService.expireOverdueJobs();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(overdueOpenJob.getStatus()).isEqualTo(JobStatus.EXPIRED);
        assertThat(futureOpenJob.getStatus()).isEqualTo(JobStatus.OPEN);
        assertThat(overdueClosedJob.getStatus()).isEqualTo(JobStatus.CLOSED);

        verify(outboxEventService).save(
                eq("JOB"),
                eq(overdueOpenJob.getId()),
                eq("JOB_EXPIRED"),
                any(),
                eq(OutboxEvent.TOPIC_JOB_EVENTS)
        );
        verify(outboxEventService, never()).save(
                eq("JOB"),
                eq(futureOpenJob.getId()),
                eq("JOB_EXPIRED"),
                any(),
                eq(OutboxEvent.TOPIC_JOB_EVENTS)
        );
        verify(outboxEventService, never()).save(
                eq("JOB"),
                eq(overdueClosedJob.getId()),
                eq("JOB_EXPIRED"),
                any(),
                eq(OutboxEvent.TOPIC_JOB_EVENTS)
        );
    }

    @Test
    @DisplayName("WANTED 원천 deadline이 없는 OPEN 공고는 자동 만료하지 않는다")
    void doNotExpireWantedOpenJobsWithoutDeadline() {
        Job wantedJobWithoutDeadline = jobRepository.save(createJob(
                "WANTED",
                "wanted-no-deadline",
                null
        ));
        Job overdueJumpitJob = jobRepository.save(createJob(
                "JUMPIT",
                "jumpit-overdue",
                LocalDateTime.of(2026, 6, 4, 11, 59)
        ));

        jobRepository.flush();

        int expiredCount = jobExpirationService.expireOverdueJobs();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(wantedJobWithoutDeadline.getStatus()).isEqualTo(JobStatus.OPEN);
        assertThat(overdueJumpitJob.getStatus()).isEqualTo(JobStatus.EXPIRED);

        verify(outboxEventService, never()).save(
                eq("JOB"),
                eq(wantedJobWithoutDeadline.getId()),
                eq("JOB_EXPIRED"),
                any(),
                eq(OutboxEvent.TOPIC_JOB_EVENTS)
        );
        verify(outboxEventService).save(
                eq("JOB"),
                eq(overdueJumpitJob.getId()),
                eq("JOB_EXPIRED"),
                any(),
                eq(OutboxEvent.TOPIC_JOB_EVENTS)
        );
    }

    @Test
    @DisplayName("만료 대상이 없으면 아무 공고도 변경하지 않는다")
    void doNothingWhenNoOverdueOpenJobs() {
        Job futureOpenJob = jobRepository.save(createJob(
                "MANUAL",
                "future-open",
                LocalDateTime.of(2026, 6, 4, 12, 1)
        ));

        jobRepository.flush();

        int expiredCount = jobExpirationService.expireOverdueJobs();

        assertThat(expiredCount).isZero();
        assertThat(futureOpenJob.getStatus()).isEqualTo(JobStatus.OPEN);

        verify(outboxEventService, never()).save(any(), any(), any(), any(), any());
    }

    private Job createJob(String source, String externalId, LocalDateTime deadlineAt) {
        return Job.create(
                source,
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

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(
                    Instant.parse("2026-06-04T03:00:00Z"),
                    ZoneId.of("Asia/Seoul")
            );
        }
    }
}
