package jobflow.domain.userjob;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobStatus;
import jobflow.domain.notification.DeadlineReminderTarget;
import jobflow.domain.notification.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJobRepository extends JpaRepository<UserJob, Long> {

    Optional<UserJob> findByUserIdAndJobId(Long userId, Long jobId);

    List<UserJob> findByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, UserJobStatus status);

    List<UserJob> findByUserIdAndStatusOrderByUpdatedAtDesc(
            Long userId,
            UserJobStatus status,
            Pageable pageable
    );

    List<UserJob> findByUserIdAndJobIdIn(Long userId, Collection<Long> jobIds);

    boolean existsByUserIdAndJobId(Long userId, Long jobId);

    @Query("""
            SELECT userJob.job
            FROM UserJob userJob
            JOIN userJob.job job
            WHERE userJob.user.id = :userId
              AND userJob.status = :userJobStatus
              AND job.status = :jobStatus
              AND job.deadlineAt IS NOT NULL
              AND job.deadlineAt > :now
              AND job.deadlineAt <= :deadlineUntil
            ORDER BY job.deadlineAt ASC, userJob.id ASC
            """)
    List<Job> findSavedOpenJobsDueSoon(
            @Param("userId") Long userId,
            @Param("userJobStatus") UserJobStatus userJobStatus,
            @Param("jobStatus") JobStatus jobStatus,
            @Param("now") LocalDateTime now,
            @Param("deadlineUntil") LocalDateTime deadlineUntil,
            Pageable pageable
    );

    @Query("""
            SELECT new jobflow.domain.notification.DeadlineReminderTarget(
                user.id,
                user.email,
                user.name,
                job.id,
                job.title,
                job.companyName,
                job.deadlineAt,
                job.originalUrl
            )
            FROM UserJob userJob
            JOIN userJob.user user
            JOIN userJob.job job
            WHERE userJob.status = :userJobStatus
              AND job.status = :jobStatus
              AND job.deadlineAt IS NOT NULL
              AND job.deadlineAt > :now
              AND job.deadlineAt <= :deadlineUntil
              AND NOT EXISTS (
                  SELECT 1
                  FROM NotificationLog notificationLog
                  WHERE notificationLog.user = user
                    AND notificationLog.job = job
                    AND notificationLog.type = :notificationType
              )
            ORDER BY job.deadlineAt ASC, userJob.id ASC
            """)
    List<DeadlineReminderTarget> findDeadlineReminderTargets(
            @Param("userJobStatus") UserJobStatus userJobStatus,
            @Param("jobStatus") JobStatus jobStatus,
            @Param("now") LocalDateTime now,
            @Param("deadlineUntil") LocalDateTime deadlineUntil,
            @Param("notificationType") NotificationType notificationType
    );
}
