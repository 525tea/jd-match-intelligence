package jobflow.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import jobflow.domain.notification.digest.DailyDigestContent;
import jobflow.domain.notification.digest.DailyDigestContentService;
import jobflow.domain.notification.digest.DailyDigestEmailRenderer;
import jobflow.domain.notification.digest.DailyDigestJobItem;
import jobflow.domain.project.ProjectSourceType;
import jobflow.domain.project.UserProject;
import jobflow.domain.project.UserProjectRepository;
import jobflow.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class DailyDigestBatchServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 17, 9, 0);

    private final UserProjectRepository userProjectRepository = org.mockito.Mockito.mock(UserProjectRepository.class);
    private final NotificationLogRepository notificationLogRepository =
            org.mockito.Mockito.mock(NotificationLogRepository.class);
    private final NotificationAttemptRepository notificationAttemptRepository =
            org.mockito.Mockito.mock(NotificationAttemptRepository.class);
    private final DailyDigestIdempotencyService idempotencyService =
            org.mockito.Mockito.mock(DailyDigestIdempotencyService.class);
    private final DailyDigestContentService contentService = org.mockito.Mockito.mock(DailyDigestContentService.class);
    private final DailyDigestEmailRenderer emailRenderer = new DailyDigestEmailRenderer();
    private final EmailSender emailSender = org.mockito.Mockito.mock(EmailSender.class);
    private final DailyDigestProperties properties = new DailyDigestProperties(null, 3);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-06-17T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    private final DailyDigestBatchService service = new DailyDigestBatchService(
            userProjectRepository,
            notificationLogRepository,
            notificationAttemptRepository,
            idempotencyService,
            contentService,
            emailRenderer,
            emailSender,
            properties,
            clock
    );

    @Test
    @DisplayName("사용자별 최신 프로젝트 기준으로 Daily Digest를 발송하고 로그와 attempt를 기록한다")
    void sendDailyDigests() {
        User user = user(1L);
        UserProject olderProject = userProject(100L, user, "older-project");
        UserProject latestProject = userProject(200L, user, "latest-project");
        given(userProjectRepository.findAllByOrderByUpdatedAtDescIdDesc())
                .willReturn(List.of(latestProject, olderProject));
        given(idempotencyService.acquire(1L, NOW.toLocalDate())).willReturn(true);
        given(notificationLogRepository.existsByUserIdAndTypeAndDeduplicationKey(
                1L,
                NotificationType.DAILY_DIGEST,
                "DAILY_DIGEST:date:2026-06-17"
        )).willReturn(false);
        given(contentService.buildDigest(
                1L,
                200L,
                List.of(JobRole.BACKEND),
                CareerLevel.MID
        )).willReturn(content());
        given(notificationLogRepository.save(any(NotificationLog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(emailSender.send(any(EmailSendRequest.class)))
                .willReturn(EmailSendResult.sent("MAILGUN", "message-id-1"));

        DailyDigestBatchResult result = service.sendDailyDigests(
                List.of(JobRole.BACKEND),
                CareerLevel.MID
        );

        assertThat(result).isEqualTo(new DailyDigestBatchResult(1, 1, 0, 0));
        ArgumentCaptor<EmailSendRequest> emailCaptor = ArgumentCaptor.forClass(EmailSendRequest.class);
        verify(emailSender).send(emailCaptor.capture());
        assertThat(emailCaptor.getValue().to()).isEqualTo("user@example.com");
        assertThat(emailCaptor.getValue().subject()).isEqualTo("[JobFlow] 오늘의 맞춤 공고 Digest");

        ArgumentCaptor<NotificationAttempt> attemptCaptor =
                ArgumentCaptor.forClass(NotificationAttempt.class);
        verify(notificationAttemptRepository).save(attemptCaptor.capture());
        NotificationAttempt attempt = attemptCaptor.getValue();
        assertThat(attempt.getStatus()).isEqualTo(NotificationAttemptStatus.SENT);
        assertThat(attempt.getProvider()).isEqualTo("MAILGUN");
        assertThat(attempt.getProviderMessageId()).isEqualTo("message-id-1");
        assertThat(attempt.getNotificationLog().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(attempt.getNotificationLog().getDeduplicationKey())
                .isEqualTo("DAILY_DIGEST:date:2026-06-17");
    }

    @Test
    @DisplayName("Redis lock 획득에 실패하면 이미 처리 중으로 보고 발송하지 않는다")
    void skipWhenIdempotencyLockIsAlreadyAcquired() {
        User user = user(1L);
        UserProject project = userProject(200L, user, "latest-project");
        given(userProjectRepository.findAllByOrderByUpdatedAtDescIdDesc()).willReturn(List.of(project));
        given(idempotencyService.acquire(1L, NOW.toLocalDate())).willReturn(false);

        DailyDigestBatchResult result = service.sendDailyDigests(
                List.of(JobRole.BACKEND),
                CareerLevel.MID
        );

        assertThat(result).isEqualTo(new DailyDigestBatchResult(1, 0, 0, 1));
        verifyNoInteractions(emailSender);
        verify(notificationLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("Digest content가 비어 있으면 발송하지 않고 lock을 해제한다")
    void skipEmptyContentAndReleaseLock() {
        User user = user(1L);
        UserProject project = userProject(200L, user, "latest-project");
        given(userProjectRepository.findAllByOrderByUpdatedAtDescIdDesc()).willReturn(List.of(project));
        given(idempotencyService.acquire(1L, NOW.toLocalDate())).willReturn(true);
        given(contentService.buildDigest(
                1L,
                200L,
                List.of(JobRole.BACKEND),
                CareerLevel.MID
        )).willReturn(new DailyDigestContent(List.of(), List.of(), List.of(), List.of()));

        DailyDigestBatchResult result = service.sendDailyDigests(
                List.of(JobRole.BACKEND),
                CareerLevel.MID
        );

        assertThat(result).isEqualTo(new DailyDigestBatchResult(1, 0, 0, 1));
        verify(idempotencyService).release(1L, NOW.toLocalDate());
        verifyNoInteractions(emailSender);
        verify(notificationLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("발송 실패 시 FAILED attempt를 남기고 로그는 재시도 가능한 PENDING 상태로 둔다")
    void recordFailedAttemptAndReleaseIdempotencyKey() {
        User user = user(1L);
        UserProject project = userProject(200L, user, "latest-project");
        given(userProjectRepository.findAllByOrderByUpdatedAtDescIdDesc()).willReturn(List.of(project));
        given(idempotencyService.acquire(1L, NOW.toLocalDate())).willReturn(true);
        given(contentService.buildDigest(
                1L,
                200L,
                List.of(JobRole.BACKEND),
                CareerLevel.MID
        )).willReturn(content());
        given(notificationLogRepository.save(any(NotificationLog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(emailSender.send(any(EmailSendRequest.class)))
                .willReturn(EmailSendResult.failed("MAILGUN", "temporary failure"));

        DailyDigestBatchResult result = service.sendDailyDigests(
                List.of(JobRole.BACKEND),
                CareerLevel.MID
        );

        assertThat(result).isEqualTo(new DailyDigestBatchResult(1, 0, 1, 0));
        ArgumentCaptor<NotificationAttempt> attemptCaptor =
                ArgumentCaptor.forClass(NotificationAttempt.class);
        verify(notificationAttemptRepository).save(attemptCaptor.capture());
        NotificationAttempt attempt = attemptCaptor.getValue();
        assertThat(attempt.getStatus()).isEqualTo(NotificationAttemptStatus.FAILED);
        assertThat(attempt.getFailureReason()).isEqualTo("temporary failure");
        assertThat(attempt.getNotificationLog().getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(attempt.getNotificationLog().getAttemptCount()).isEqualTo(1);
        assertThat(attempt.getNotificationLog().getNextRetryAt()).isEqualTo(NOW.plusMinutes(10));
        verify(idempotencyService).release(1L, "DAILY_DIGEST:date:2026-06-17");
    }

    private DailyDigestContent content() {
        return new DailyDigestContent(
                List.of(new DailyDigestJobItem(
                        10L,
                        "백엔드 개발자",
                        "Example Company",
                        JobRole.BACKEND,
                        CareerLevel.MID,
                        BigDecimal.valueOf(82.67),
                        NOW.plusDays(1),
                        "https://example.com/jobs/10",
                        "추천 점수 기반"
                )),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private User user(Long id) {
        User user = User.signup("user@example.com", "encoded-password", "사용자");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UserProject userProject(Long id, User user, String name) {
        UserProject userProject = instantiateUserProject();
        ReflectionTestUtils.setField(userProject, "id", id);
        ReflectionTestUtils.setField(userProject, "user", user);
        ReflectionTestUtils.setField(userProject, "sourceType", ProjectSourceType.GITHUB);
        ReflectionTestUtils.setField(userProject, "externalId", "external-" + id);
        ReflectionTestUtils.setField(userProject, "name", name);
        ReflectionTestUtils.setField(userProject, "repositoryUrl", "https://example.com/" + name);
        return userProject;
    }

    private UserProject instantiateUserProject() {
        try {
            var constructor = UserProject.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to instantiate UserProject test fixture", exception);
        }
    }
}
