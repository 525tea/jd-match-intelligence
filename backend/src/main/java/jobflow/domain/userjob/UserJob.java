package jobflow.domain.userjob;

import jakarta.persistence.*;
import jobflow.domain.common.BaseTimeEntity;
import jobflow.domain.job.Job;
import jobflow.domain.user.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "user_jobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_jobs_user_job",
                columnNames = {"user_id", "job_id"}
        ),
        indexes = {
                @Index(name = "idx_user_jobs_user_status", columnList = "user_id,status"),
                @Index(name = "idx_user_jobs_job_status", columnList = "job_id,status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserJob extends BaseTimeEntity {

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
    private UserJobStatus status = UserJobStatus.VIEWED;

    private LocalDateTime viewedAt;

    private LocalDateTime savedAt;

    private LocalDateTime ignoredAt;

    public static UserJob viewed(User user, Job job, LocalDateTime viewedAt) {
        UserJob userJob = new UserJob();
        userJob.user = user;
        userJob.job = job;
        userJob.status = UserJobStatus.VIEWED;
        userJob.viewedAt = viewedAt;
        return userJob;
    }

    public void markViewed(LocalDateTime viewedAt) {
        this.status = UserJobStatus.VIEWED;
        this.viewedAt = viewedAt;
    }

    public void save(LocalDateTime savedAt) {
        this.status = UserJobStatus.SAVED;
        this.savedAt = savedAt;
    }

    public void ignore(LocalDateTime ignoredAt) {
        this.status = UserJobStatus.IGNORED;
        this.ignoredAt = ignoredAt;
    }

    public boolean isSaved() {
        return status == UserJobStatus.SAVED;
    }

    public boolean isIgnored() {
        return status == UserJobStatus.IGNORED;
    }
}
