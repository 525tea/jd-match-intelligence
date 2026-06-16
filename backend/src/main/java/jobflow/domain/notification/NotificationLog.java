package jobflow.domain.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jobflow.domain.common.BaseTimeEntity;
import jobflow.domain.job.Job;
import jobflow.domain.user.User;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "notification_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_logs_user_job_type",
                columnNames = {"user_id", "job_id", "type"}
        ),
        indexes = {
                @Index(name = "idx_notification_logs_type_status_retry", columnList = "type,status,next_retry_at"),
                @Index(name = "idx_notification_logs_user", columnList = "user_id"),
                @Index(name = "idx_notification_logs_job", columnList = "job_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationLog extends BaseTimeEntity {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;

    private LocalDateTime nextRetryAt;

    private LocalDateTime lastAttemptedAt;

    private LocalDateTime sentAt;

    public static NotificationLog create(
            User user,
            Job job,
            NotificationType type,
            LocalDateTime now
    ) {
        return create(user, job, type, DEFAULT_MAX_ATTEMPTS, now);
    }

    public static NotificationLog create(
            User user,
            Job job,
            NotificationType type,
            int maxAttempts,
            LocalDateTime now
    ) {
        NotificationLog notificationLog = new NotificationLog();
        notificationLog.user = user;
        notificationLog.job = job;
        notificationLog.type = type;
        notificationLog.status = NotificationStatus.PENDING;
        notificationLog.attemptCount = 0;
        notificationLog.maxAttempts = maxAttempts;
        notificationLog.nextRetryAt = now;
        return notificationLog;
    }

    public int nextAttemptNumber() {
        return attemptCount + 1;
    }

    public boolean canRetry(LocalDateTime now) {
        return status == NotificationStatus.PENDING
                && attemptCount < maxAttempts
                && (nextRetryAt == null || !nextRetryAt.isAfter(now));
    }

    public void markSent(LocalDateTime attemptedAt) {
        this.attemptCount++;
        this.status = NotificationStatus.SENT;
        this.lastAttemptedAt = attemptedAt;
        this.sentAt = attemptedAt;
        this.nextRetryAt = null;
    }

    public void markFailed(LocalDateTime attemptedAt, LocalDateTime nextRetryAt) {
        this.attemptCount++;
        this.lastAttemptedAt = attemptedAt;
        if (attemptCount >= maxAttempts) {
            this.status = NotificationStatus.FAILED;
            this.nextRetryAt = null;
            return;
        }
        this.status = NotificationStatus.PENDING;
        this.nextRetryAt = nextRetryAt;
    }

    public void skip() {
        this.status = NotificationStatus.SKIPPED;
        this.nextRetryAt = null;
    }
}
