package jobflow.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.RemoteType;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.domain.userjob.UserJobRepository;
import jobflow.domain.userjob.UserJobStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class DeadlineReminderBatchServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 16, 10, 0);

    private final UserJobRepository userJobRepository = org.mockito.Mockito.mock(UserJobRepository.class);
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final JobRepository jobRepository = org.mockito.Mockito.mock(JobRepository.class);
    private final NotificationLogRepository notificationLogRepository =
            org.mockito.Mockito.mock(NotificationLogRepository.class);
    private final NotificationAttemptRepository notificationAttemptRepository =
            org.mockito.Mockito.mock(NotificationAttemptRepository.class);
    private final DeadlineReminderIdempotencyService idempotencyService =
            org.mockito.Mockito.mock(DeadlineReminderIdempotencyService.class);
    private final EmailSender emailSender = org.mockito.Mockito.mock(EmailSender.class);
    private final DeadlineReminderProperties properties = new DeadlineReminderProperties(
            Duration.ofHours(24),
            Duration.ofHours(25),
            3
    );
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-06-16T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private final DeadlineReminderBatchService service = new DeadlineReminderBatchService(
            userJobRepository,
            userRepository,
            jobRepository,
            notificationLogRepository,
            notificationAttemptRepository,
            idempotencyService,
            new DeadlineReminderEmailRenderer(),
            emailSender,
            properties,
            clock
    );

    @Test
    @DisplayName("마감 임박 저장 공고 알림을 발송하고 로그와 attempt를 SENT로 기록한다")
    void sendDueSoonReminders() {
        DeadlineReminderTarget target = target(1L, 10L);
        User user = user(1L);
        Job job = job(10L);
        given(userJobRepository.findDeadlineReminderTargets(
                UserJobStatus.SAVED,
                jobflow.domain.job.JobStatus.OPEN,
                NOW,
                NOW.plusHours(24),
                NotificationType.DEADLINE_REMINDER
        )).willReturn(List.of(target));
        given(idempotencyService.acquire(1L, 10L)).willReturn(true);
        given(notificationLogRepository.existsByUserIdAndJobIdAndType(
                1L,
                10L,
                NotificationType.DEADLINE_REMINDER
        )).willReturn(false);
        given(userRepository.getReferenceById(1L)).willReturn(user);
        given(jobRepository.getReferenceById(10L)).willReturn(job);
        given(notificationLogRepository.save(any(NotificationLog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(emailSender.send(any(EmailSendRequest.class)))
                .willReturn(EmailSendResult.sent("MAILGUN", "message-id-1"));

        DeadlineReminderBatchResult result = service.sendDueSoonReminders();

        assertThat(result).isEqualTo(new DeadlineReminderBatchResult(1, 1, 0, 0));
        ArgumentCaptor<NotificationAttempt> attemptCaptor =
                ArgumentCaptor.forClass(NotificationAttempt.class);
        verify(notificationAttemptRepository).save(attemptCaptor.capture());
        NotificationAttempt attempt = attemptCaptor.getValue();
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        assertThat(attempt.getStatus()).isEqualTo(NotificationAttemptStatus.SENT);
        assertThat(attempt.getProvider()).isEqualTo("MAILGUN");
        assertThat(attempt.getProviderMessageId()).isEqualTo("message-id-1");
    }

    @Test
    @DisplayName("SETNX 획득에 실패하면 이미 처리 중으로 보고 발송하지 않는다")
    void skipWhenIdempotencyLockIsAlreadyAcquired() {
        DeadlineReminderTarget target = target(1L, 10L);
        given(userJobRepository.findDeadlineReminderTargets(
                UserJobStatus.SAVED,
                jobflow.domain.job.JobStatus.OPEN,
                NOW,
                NOW.plusHours(24),
                NotificationType.DEADLINE_REMINDER
        )).willReturn(List.of(target));
        given(idempotencyService.acquire(1L, 10L)).willReturn(false);

        DeadlineReminderBatchResult result = service.sendDueSoonReminders();

        assertThat(result).isEqualTo(new DeadlineReminderBatchResult(1, 0, 0, 1));
        verifyNoInteractions(emailSender);
        verify(notificationLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("발송 실패 시 FAILED attempt를 남기고 로그는 재시도 가능한 PENDING 상태로 둔다")
    void recordFailedAttemptAndReleaseIdempotencyKey() {
        DeadlineReminderTarget target = target(1L, 10L);
        User user = user(1L);
        Job job = job(10L);
        given(userJobRepository.findDeadlineReminderTargets(
                UserJobStatus.SAVED,
                jobflow.domain.job.JobStatus.OPEN,
                NOW,
                NOW.plusHours(24),
                NotificationType.DEADLINE_REMINDER
        )).willReturn(List.of(target));
        given(idempotencyService.acquire(1L, 10L)).willReturn(true);
        given(userRepository.getReferenceById(1L)).willReturn(user);
        given(jobRepository.getReferenceById(10L)).willReturn(job);
        given(notificationLogRepository.save(any(NotificationLog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(emailSender.send(any(EmailSendRequest.class)))
                .willReturn(EmailSendResult.failed("MAILGUN", "temporary failure"));

        DeadlineReminderBatchResult result = service.sendDueSoonReminders();

        assertThat(result).isEqualTo(new DeadlineReminderBatchResult(1, 0, 1, 0));
        ArgumentCaptor<NotificationAttempt> attemptCaptor =
                ArgumentCaptor.forClass(NotificationAttempt.class);
        verify(notificationAttemptRepository).save(attemptCaptor.capture());
        NotificationAttempt attempt = attemptCaptor.getValue();
        assertThat(attempt.getStatus()).isEqualTo(NotificationAttemptStatus.FAILED);
        assertThat(attempt.getFailureReason()).isEqualTo("temporary failure");
        assertThat(attempt.getNotificationLog().getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(attempt.getNotificationLog().getAttemptCount()).isEqualTo(1);
        assertThat(attempt.getNotificationLog().getNextRetryAt()).isEqualTo(NOW.plusMinutes(10));
        verify(idempotencyService).release(1L, 10L);
    }

    @Test
    @DisplayName("재시도 가능한 PENDING 로그를 다시 발송한다")
    void retryPendingReminders() {
        User user = user(1L);
        Job job = job(10L);
        NotificationLog notificationLog = NotificationLog.create(
                user,
                job,
                NotificationType.DEADLINE_REMINDER,
                3,
                NOW.minusMinutes(1)
        );
        given(notificationLogRepository.findByTypeAndStatusAndNextRetryAtLessThanEqual(
                NotificationType.DEADLINE_REMINDER,
                NotificationStatus.PENDING,
                NOW
        )).willReturn(List.of(notificationLog));
        given(idempotencyService.acquire(1L, 10L)).willReturn(true);
        given(emailSender.send(any(EmailSendRequest.class)))
                .willReturn(EmailSendResult.sent("MAILGUN", "message-id-2"));

        DeadlineReminderBatchResult result = service.retryPendingReminders();

        assertThat(result).isEqualTo(new DeadlineReminderBatchResult(1, 1, 0, 0));
        assertThat(notificationLog.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notificationLog.getAttemptCount()).isEqualTo(1);
    }

    private DeadlineReminderTarget target(Long userId, Long jobId) {
        return new DeadlineReminderTarget(
                userId,
                "user@example.com",
                "사용자",
                jobId,
                "백엔드 개발자",
                "Example Company",
                NOW.plusHours(3),
                "https://example.com/jobs/" + jobId
        );
    }

    private User user(Long id) {
        User user = User.signup("user@example.com", "encoded-password", "사용자");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Job job(Long id) {
        Job job = Job.create(
                "MANUAL",
                "deadline-reminder-test-job",
                "백엔드 개발자",
                "Example Company",
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
                NOW.plusHours(3)
        );
        ReflectionTestUtils.setField(job, "id", id);
        return job;
    }
}
