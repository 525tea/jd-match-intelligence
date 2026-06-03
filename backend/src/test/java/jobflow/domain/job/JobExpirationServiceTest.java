package jobflow.domain.job;

import jobflow.domain.outbox.OutboxEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobExpirationServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private OutboxEventService outboxEventService;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-06-04T03:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    private JobExpirationService jobExpirationService;

    @BeforeEach
    void setUp() {
        jobExpirationService = new JobExpirationService(
                jobRepository,
                outboxEventService,
                clock
        );
    }

    @Test
    @DisplayName("마감 시간이 지난 OPEN 공고를 EXPIRED로 변경하고 outbox event를 저장한다")
    void expireOverdueJobs() {
        Job job = createJob(1L, LocalDateTime.now().minusDays(1));

        given(jobRepository.findByStatusAndDeadlineAtBefore(eq(JobStatus.OPEN), any(LocalDateTime.class)))
                .willReturn(List.of(job));

        int expiredCount = jobExpirationService.expireOverdueJobs();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(JobStatus.EXPIRED);

        verify(outboxEventService).save(
                eq("JOB"),
                eq(1L),
                eq("JOB_EXPIRED"),
                any(),
                eq("job.events")
        );
    }

    @Test
    @DisplayName("만료 대상 공고가 없으면 상태 변경과 outbox 저장을 하지 않는다")
    void expireOverdueJobsWhenEmpty() {
        given(jobRepository.findByStatusAndDeadlineAtBefore(eq(JobStatus.OPEN), any(LocalDateTime.class)))
                .willReturn(List.of());

        int expiredCount = jobExpirationService.expireOverdueJobs();

        assertThat(expiredCount).isZero();
        verify(outboxEventService, never()).save(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("OPEN 상태 공고만 현재 시간 기준으로 만료 조회 대상으로 요청한다")
    void findOnlyOpenJobs() {
        given(jobRepository.findByStatusAndDeadlineAtBefore(eq(JobStatus.OPEN), any(LocalDateTime.class)))
                .willReturn(List.of());

        jobExpirationService.expireOverdueJobs();

        ArgumentCaptor<JobStatus> statusCaptor = ArgumentCaptor.forClass(JobStatus.class);
        ArgumentCaptor<LocalDateTime> deadlineCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(jobRepository).findByStatusAndDeadlineAtBefore(
                statusCaptor.capture(),
                deadlineCaptor.capture()
        );

        assertThat(statusCaptor.getValue()).isEqualTo(JobStatus.OPEN);
        assertThat(deadlineCaptor.getValue()).isEqualTo("2026-06-04T12:00:00");
    }

    private Job createJob(Long id, LocalDateTime deadlineAt) {
        Job job = Job.create(
                "MANUAL",
                "job-" + id,
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 백엔드 개발자 채용",
                "https://example.com/jobs/" + id,
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
        ReflectionTestUtils.setField(job, "id", id);

        return job;
    }
}
