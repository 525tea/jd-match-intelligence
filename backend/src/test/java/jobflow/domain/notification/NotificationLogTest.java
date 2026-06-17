package jobflow.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.RemoteType;
import jobflow.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationLogTest {

    @Test
    @DisplayName("마감 알림 로그는 PENDING 상태와 첫 재시도 시각으로 생성된다")
    void createPendingNotificationLog() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 10, 0);

        NotificationLog notificationLog = NotificationLog.create(
                User.signup("user@example.com", "encoded-password", "사용자"),
                createJob(),
                NotificationType.DEADLINE_REMINDER,
                now
        );

        assertThat(notificationLog.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notificationLog.getAttemptCount()).isZero();
        assertThat(notificationLog.getMaxAttempts()).isEqualTo(3);
        assertThat(notificationLog.getNextRetryAt()).isEqualTo(now);
        assertThat(notificationLog.getDeduplicationKey()).isEqualTo("DEADLINE_REMINDER:job:10");
        assertThat(notificationLog.canRetry(now)).isTrue();
    }

    @Test
    @DisplayName("Daily Digest 로그는 job 없이 날짜 기준 deduplication key로 생성된다")
    void createDailyDigestNotificationLog() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 17, 9, 0);

        NotificationLog notificationLog = NotificationLog.createDailyDigest(
                User.signup("user@example.com", "encoded-password", "사용자"),
                LocalDate.of(2026, 6, 17),
                3,
                now
        );

        assertThat(notificationLog.getJob()).isNull();
        assertThat(notificationLog.getType()).isEqualTo(NotificationType.DAILY_DIGEST);
        assertThat(notificationLog.getDeduplicationKey()).isEqualTo("DAILY_DIGEST:date:2026-06-17");
        assertThat(notificationLog.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notificationLog.getAttemptCount()).isZero();
        assertThat(notificationLog.getMaxAttempts()).isEqualTo(3);
        assertThat(notificationLog.getNextRetryAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("job-level 알림 로그는 job 없이 생성할 수 없다")
    void rejectJobLevelNotificationLogWithoutJob() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 17, 9, 0);

        assertThatThrownBy(() -> NotificationLog.create(
                User.signup("user@example.com", "encoded-password", "사용자"),
                null,
                NotificationType.DEADLINE_REMINDER,
                3,
                now
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("job is required for job-level notification log");
    }

    @Test
    @DisplayName("발송 성공 시 SENT 상태로 변경하고 재시도 대상에서 제외한다")
    void markSent() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 10, 0);
        NotificationLog notificationLog = NotificationLog.create(
                User.signup("user@example.com", "encoded-password", "사용자"),
                createJob(),
                NotificationType.DEADLINE_REMINDER,
                now
        );

        notificationLog.markSent(now.plusMinutes(1));

        assertThat(notificationLog.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notificationLog.getAttemptCount()).isEqualTo(1);
        assertThat(notificationLog.getSentAt()).isEqualTo(now.plusMinutes(1));
        assertThat(notificationLog.getNextRetryAt()).isNull();
        assertThat(notificationLog.canRetry(now.plusMinutes(2))).isFalse();
    }

    @Test
    @DisplayName("발송 실패 후 남은 시도 횟수가 있으면 PENDING 상태로 다음 재시도 시각을 저장한다")
    void markFailedForRetry() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 10, 0);
        NotificationLog notificationLog = NotificationLog.create(
                User.signup("user@example.com", "encoded-password", "사용자"),
                createJob(),
                NotificationType.DEADLINE_REMINDER,
                2,
                now
        );

        notificationLog.markFailed(now.plusMinutes(1), now.plusMinutes(10));

        assertThat(notificationLog.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notificationLog.getAttemptCount()).isEqualTo(1);
        assertThat(notificationLog.getNextRetryAt()).isEqualTo(now.plusMinutes(10));
        assertThat(notificationLog.canRetry(now.plusMinutes(9))).isFalse();
        assertThat(notificationLog.canRetry(now.plusMinutes(10))).isTrue();
    }

    @Test
    @DisplayName("최대 시도 횟수에 도달한 실패는 최종 FAILED 상태가 된다")
    void markFailedFinally() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 10, 0);
        NotificationLog notificationLog = NotificationLog.create(
                User.signup("user@example.com", "encoded-password", "사용자"),
                createJob(),
                NotificationType.DEADLINE_REMINDER,
                1,
                now
        );

        notificationLog.markFailed(now.plusMinutes(1), now.plusMinutes(10));

        assertThat(notificationLog.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notificationLog.getAttemptCount()).isEqualTo(1);
        assertThat(notificationLog.getNextRetryAt()).isNull();
        assertThat(notificationLog.canRetry(now.plusMinutes(10))).isFalse();
    }

    private Job createJob() {
        Job job = Job.create(
                "MANUAL",
                "deadline-reminder-test-job",
                "백엔드 개발자",
                "Example Company",
                "Spring Boot 백엔드 개발자 채용",
                "https://example.com/jobs/deadline-reminder-test-job",
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
                LocalDateTime.of(2026, 6, 17, 9, 0)
        );
        ReflectionTestUtils.setField(job, "id", 10L);
        return job;
    }
}
