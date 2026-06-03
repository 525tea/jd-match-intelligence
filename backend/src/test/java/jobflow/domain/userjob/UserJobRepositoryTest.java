package jobflow.domain.userjob;

import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.RemoteType;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
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

    private Job createJob(String externalId) {
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
                LocalDateTime.of(2026, 6, 30, 23, 59)
        );
    }
}
