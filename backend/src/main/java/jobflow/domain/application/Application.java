package jobflow.domain.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import jobflow.domain.common.BaseTimeEntity;
import jobflow.domain.job.Job;
import jobflow.domain.user.User;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.ConflictException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "applications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseTimeEntity {

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
    @Column(nullable = false, length = 30)
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    @Version
    @Column(nullable = false)
    private Long version;

    private LocalDateTime appliedAt;

    public static Application create(User user, Job job) {
        Application application = new Application();
        application.user = user;
        application.job = job;
        application.status = ApplicationStatus.APPLIED;
        application.appliedAt = LocalDateTime.now();
        return application;
    }

    public void changeStatus(ApplicationStatus nextStatus) {
        if (isTerminalStatus() && status != nextStatus) {
            throw new ConflictException(ErrorCode.APPLICATION_STATUS_CONFLICT);
        }

        this.status = nextStatus;
    }

    private boolean isTerminalStatus() {
        return status == ApplicationStatus.REJECTED
                || status == ApplicationStatus.WITHDRAWN;
    }
}
