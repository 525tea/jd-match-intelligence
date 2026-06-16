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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "notification_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_attempts_log_attempt",
                columnNames = {"notification_log_id", "attempt_number"}
        ),
        indexes = @Index(name = "idx_notification_attempts_status_attempted", columnList = "status,attempted_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_log_id", nullable = false)
    private NotificationLog notificationLog;

    @Column(nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationAttemptStatus status;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(length = 255)
    private String providerMessageId;

    @Lob
    @Column(columnDefinition = "text")
    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime attemptedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static NotificationAttempt sent(
            NotificationLog notificationLog,
            int attemptNumber,
            String provider,
            String providerMessageId,
            LocalDateTime attemptedAt
    ) {
        NotificationAttempt attempt = base(notificationLog, attemptNumber, provider, attemptedAt);
        attempt.status = NotificationAttemptStatus.SENT;
        attempt.providerMessageId = providerMessageId;
        return attempt;
    }

    public static NotificationAttempt failed(
            NotificationLog notificationLog,
            int attemptNumber,
            String provider,
            String failureReason,
            LocalDateTime attemptedAt
    ) {
        NotificationAttempt attempt = base(notificationLog, attemptNumber, provider, attemptedAt);
        attempt.status = NotificationAttemptStatus.FAILED;
        attempt.failureReason = failureReason;
        return attempt;
    }

    private static NotificationAttempt base(
            NotificationLog notificationLog,
            int attemptNumber,
            String provider,
            LocalDateTime attemptedAt
    ) {
        NotificationAttempt attempt = new NotificationAttempt();
        attempt.notificationLog = notificationLog;
        attempt.attemptNumber = attemptNumber;
        attempt.provider = provider;
        attempt.attemptedAt = attemptedAt;
        attempt.createdAt = attemptedAt;
        return attempt;
    }
}
