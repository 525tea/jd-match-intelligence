package jobflow.domain.userjob;

import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.RemoteType;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.domain.notification.NotificationLog;
import jobflow.domain.notification.NotificationLogRepository;
import jobflow.domain.notification.NotificationType;
import jobflow.domain.notification.DeadlineReminderTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import jobflow.global.config.JpaAuditingConfig;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class UserJobRepositoryTest {

    @Autowired
    private UserJobRepository userJobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Test
    @DisplayName("사용자와 공고 id로 UserJob을 조회한다")
    void findByUserIdAndJobId() {
        User user = userRepository.save(User.signup("user@example.com", "encoded-password", "사용자"));
        Job job = jobRepository.save(createJob("job-1"));
        UserJob userJob = userJobRepository.save(UserJob.viewed(
                user,
                job,
                LocalDateTime.of(2026, 6, 4, 10, 0)
        ));

        Optional<UserJob> found = userJobRepository.findByUserIdAndJobId(user.getId(), job.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(userJob.getId());
        assertThat(found.get().getStatus()).isEqualTo(UserJobStatus.VIEWED);
    }

    @Test
    @DisplayName("같은 사용자와 공고에는 UserJob을 중복 생성할 수 없다")
    void preventDuplicateUserJob() {
        User user = userRepository.save(User.signup("user@example.com", "encoded-password", "사용자"));
        Job job = jobRepository.save(createJob("job-1"));

        userJobRepository.save(UserJob.viewed(
                user,
                job,
                LocalDateTime.of(2026, 6, 4, 10, 0)
        ));
        userJobRepository.flush();

        assertThatThrownBy(() -> {
            userJobRepository.save(UserJob.viewed(
                    user,
                    job,
                    LocalDateTime.of(2026, 6, 4, 11, 0)
            ));
            userJobRepository.flush();
        })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("사용자의 특정 상태 UserJob 목록을 조회한다")
    void findByUserIdAndStatus() {
        User user = userRepository.save(User.signup("user@example.com", "encoded-password", "사용자"));
        Job savedJob = jobRepository.save(createJob("saved-job"));
        Job ignoredJob = jobRepository.save(createJob("ignored-job"));

        UserJob savedUserJob = UserJob.viewed(
                user,
                savedJob,
                LocalDateTime.of(2026, 6, 4, 10, 0)
        );
        savedUserJob.save(LocalDateTime.of(2026, 6, 4, 11, 0));

        UserJob ignoredUserJob = UserJob.viewed(
                user,
                ignoredJob,
                LocalDateTime.of(2026, 6, 4, 10, 0)
        );
        ignoredUserJob.ignore(LocalDateTime.of(2026, 6, 4, 11, 0));

        userJobRepository.save(savedUserJob);
        userJobRepository.save(ignoredUserJob);
        userJobRepository.flush();

        List<UserJob> savedUserJobs = userJobRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(
                user.getId(),
                UserJobStatus.SAVED
        );

        assertThat(savedUserJobs).hasSize(1);
        assertThat(savedUserJobs.getFirst().getJob().getExternalId()).isEqualTo("saved-job");
        assertThat(savedUserJobs.getFirst().getStatus()).isEqualTo(UserJobStatus.SAVED);
    }

    @Test
    @DisplayName("사용자와 공고의 UserJob 존재 여부를 확인한다")
    void existsByUserIdAndJobId() {
        User user = userRepository.save(User.signup("user@example.com", "encoded-password", "사용자"));
        Job job = jobRepository.save(createJob("job-1"));

        userJobRepository.save(UserJob.viewed(
                user,
                job,
                LocalDateTime.of(2026, 6, 4, 10, 0)
        ));

        boolean exists = userJobRepository.existsByUserIdAndJobId(user.getId(), job.getId());

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("저장한 OPEN 공고 중 24시간 이내 마감이고 아직 알림 로그가 없는 대상만 조회한다")
    void findDeadlineReminderTargets() {
        User user = userRepository.save(User.signup("user@example.com", "encoded-password", "사용자"));
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 10, 0);
        Job targetJob = jobRepository.save(createJob(
                "target-job",
                now.plusHours(3)
        ));
        Job alreadyLoggedJob = jobRepository.save(createJob(
                "already-logged-job",
                now.plusHours(4)
        ));
        Job viewedJob = jobRepository.save(createJob(
                "viewed-job",
                now.plusHours(5)
        ));
        Job farDeadlineJob = jobRepository.save(createJob(
                "far-deadline-job",
                now.plusHours(25)
        ));
        Job nullDeadlineJob = jobRepository.save(createJob(
                "null-deadline-job",
                null
        ));
        Job closedJob = jobRepository.save(createJob(
                "closed-job",
                now.plusHours(6)
        ));
        closedJob.close();

        UserJob targetUserJob = UserJob.viewed(user, targetJob, now.minusHours(1));
        targetUserJob.save(now.minusMinutes(50));
        UserJob alreadyLoggedUserJob = UserJob.viewed(user, alreadyLoggedJob, now.minusHours(1));
        alreadyLoggedUserJob.save(now.minusMinutes(40));
        UserJob viewedUserJob = UserJob.viewed(user, viewedJob, now.minusHours(1));
        UserJob farDeadlineUserJob = UserJob.viewed(user, farDeadlineJob, now.minusHours(1));
        farDeadlineUserJob.save(now.minusMinutes(30));
        UserJob nullDeadlineUserJob = UserJob.viewed(user, nullDeadlineJob, now.minusHours(1));
        nullDeadlineUserJob.save(now.minusMinutes(20));
        UserJob closedUserJob = UserJob.viewed(user, closedJob, now.minusHours(1));
        closedUserJob.save(now.minusMinutes(10));
        userJobRepository.saveAll(List.of(
                targetUserJob,
                alreadyLoggedUserJob,
                viewedUserJob,
                farDeadlineUserJob,
                nullDeadlineUserJob,
                closedUserJob
        ));
        notificationLogRepository.save(NotificationLog.create(
                user,
                alreadyLoggedJob,
                NotificationType.DEADLINE_REMINDER,
                now.minusMinutes(5)
        ));
        userJobRepository.flush();

        List<DeadlineReminderTarget> targets = userJobRepository.findDeadlineReminderTargets(
                UserJobStatus.SAVED,
                jobflow.domain.job.JobStatus.OPEN,
                now,
                now.plusHours(24),
                NotificationType.DEADLINE_REMINDER
        );

        assertThat(targets).hasSize(1);
        DeadlineReminderTarget target = targets.getFirst();
        assertThat(target.userId()).isEqualTo(user.getId());
        assertThat(target.userEmail()).isEqualTo("user@example.com");
        assertThat(target.jobId()).isEqualTo(targetJob.getId());
        assertThat(target.jobTitle()).isEqualTo("백엔드 개발자");
        assertThat(target.deadlineAt()).isEqualTo(now.plusHours(3));
    }

    private Job createJob(String externalId) {
        return createJob(externalId, LocalDateTime.of(2026, 6, 30, 23, 59));
    }

    private Job createJob(String externalId, LocalDateTime deadlineAt) {
        return Job.create(
                "MANUAL",
                externalId,
                "백엔드 개발자",
                "JobFlow",
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
                deadlineAt
        );
    }
}
