package jobflow.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.RemoteType;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class NotificationRepositoryTest {

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Autowired
    private NotificationAttemptRepository notificationAttemptRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Test
    @DisplayName("사용자/공고/알림 타입 단위로 NotificationLog 중복 생성을 막는다")
    void preventDuplicateNotificationLog() {
        User user = userRepository.save(User.signup("user@example.com", "encoded-password", "사용자"));
        Job job = jobRepository.save(createJob("deadline-reminder-test-job"));
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 10, 0);

        notificationLogRepository.save(NotificationLog.create(
                user,
                job,
                NotificationType.DEADLINE_REMINDER,
                now
        ));
        notificationLogRepository.flush();

        assertThatThrownBy(() -> {
            notificationLogRepository.save(NotificationLog.create(
                    user,
                    job,
                    NotificationType.DEADLINE_REMINDER,
                    now.plusMinutes(1)
            ));
            notificationLogRepository.flush();
        })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("재시도 가능한 PENDING 알림 로그를 조회한다")
    void findRetryablePendingLogs() {
        User user = userRepository.save(User.signup("user@example.com", "encoded-password", "사용자"));
        Job retryableJob = jobRepository.save(createJob("retryable-job"));
        Job futureJob = jobRepository.save(createJob("future-job"));
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 10, 0);

        NotificationLog retryableLog = notificationLogRepository.save(NotificationLog.create(
                user,
                retryableJob,
                NotificationType.DEADLINE_REMINDER,
                now.minusMinutes(1)
        ));
        notificationLogRepository.save(NotificationLog.create(
                user,
                futureJob,
                NotificationType.DEADLINE_REMINDER,
                now.plusMinutes(10)
        ));

        List<NotificationLog> retryableLogs =
                notificationLogRepository.findByTypeAndStatusAndNextRetryAtLessThanEqual(
                        NotificationType.DEADLINE_REMINDER,
                        NotificationStatus.PENDING,
                        now
                );

        assertThat(retryableLogs)
                .extracting(NotificationLog::getId)
                .containsExactly(retryableLog.getId());
    }

    @Test
    @DisplayName("NotificationAttempt는 로그별 attempt number 중복 생성을 막는다")
    void preventDuplicateAttemptNumber() {
        User user = userRepository.save(User.signup("user@example.com", "encoded-password", "사용자"));
        Job job = jobRepository.save(createJob("attempt-job"));
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 10, 0);
        NotificationLog notificationLog = notificationLogRepository.save(NotificationLog.create(
                user,
                job,
                NotificationType.DEADLINE_REMINDER,
                now
        ));
        notificationAttemptRepository.save(NotificationAttempt.sent(
                notificationLog,
                1,
                "MAILGUN",
                "message-id-1",
                now
        ));
        notificationAttemptRepository.flush();

        assertThatThrownBy(() -> {
            notificationAttemptRepository.save(NotificationAttempt.failed(
                    notificationLog,
                    1,
                    "MAILGUN",
                    "temporary failure",
                    now.plusMinutes(1)
            ));
            notificationAttemptRepository.flush();
        })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("NotificationAttempt를 attempt number 순서로 조회한다")
    void findAttemptsInOrder() {
        User user = userRepository.save(User.signup("user@example.com", "encoded-password", "사용자"));
        Job job = jobRepository.save(createJob("ordered-attempt-job"));
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 10, 0);
        NotificationLog notificationLog = notificationLogRepository.save(NotificationLog.create(
                user,
                job,
                NotificationType.DEADLINE_REMINDER,
                now
        ));
        NotificationAttempt first = notificationAttemptRepository.save(NotificationAttempt.failed(
                notificationLog,
                1,
                "MAILGUN",
                "temporary failure",
                now
        ));
        NotificationAttempt second = notificationAttemptRepository.save(NotificationAttempt.sent(
                notificationLog,
                2,
                "MAILGUN",
                "message-id-2",
                now.plusMinutes(10)
        ));

        List<NotificationAttempt> attempts =
                notificationAttemptRepository.findByNotificationLogIdOrderByAttemptNumberAsc(
                        notificationLog.getId()
                );

        assertThat(attempts).containsExactly(first, second);
    }

    private Job createJob(String externalId) {
        return Job.create(
                "MANUAL",
                externalId,
                "백엔드 개발자",
                "Example Company",
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
                LocalDateTime.of(2026, 6, 17, 9, 0)
        );
    }
}
