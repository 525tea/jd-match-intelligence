package jobflow.domain.userjob;

import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.RemoteType;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.domain.userjob.dto.UserJobResponse;
import jobflow.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        JpaAuditingConfig.class,
        UserJobService.class
})
class UserJobServiceFlowTest {

    @Autowired
    private UserJobService userJobService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private UserJobRepository userJobRepository;

    @Test
    @DisplayName("사용자는 공고를 조회한 뒤 저장 상태로 변경할 수 있다")
    void viewAndSaveJob() {
        User user = userRepository.save(User.signup("user@example.com", "encoded-password", "사용자"));
        Job job = jobRepository.save(createJob("job-1"));

        UserJobResponse viewedResponse = userJobService.markViewed(user.getId(), job.getId());
        UserJobResponse savedResponse = userJobService.saveJob(user.getId(), job.getId());

        assertThat(viewedResponse.status()).isEqualTo(UserJobStatus.VIEWED);
        assertThat(savedResponse.id()).isEqualTo(viewedResponse.id());
        assertThat(savedResponse.status()).isEqualTo(UserJobStatus.SAVED);
        assertThat(savedResponse.viewedAt()).isNotNull();
        assertThat(savedResponse.savedAt()).isNotNull();
        assertThat(userJobRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("사용자는 저장한 공고를 무시 상태로 변경할 수 있다")
    void saveAndIgnoreJob() {
        User user = userRepository.save(User.signup("user@example.com", "encoded-password", "사용자"));
        Job job = jobRepository.save(createJob("job-1"));

        UserJobResponse savedResponse = userJobService.saveJob(user.getId(), job.getId());
        UserJobResponse ignoredResponse = userJobService.ignoreJob(user.getId(), job.getId());

        assertThat(savedResponse.status()).isEqualTo(UserJobStatus.SAVED);
        assertThat(ignoredResponse.id()).isEqualTo(savedResponse.id());
        assertThat(ignoredResponse.status()).isEqualTo(UserJobStatus.IGNORED);
        assertThat(ignoredResponse.ignoredAt()).isNotNull();
        assertThat(userJobRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("상태별 내 공고 목록을 조회한다")
    void getMyJobsByStatus() {
        User user = userRepository.save(User.signup("user@example.com", "encoded-password", "사용자"));
        Job viewedJob = jobRepository.save(createJob("viewed-job"));
        Job savedJob = jobRepository.save(createJob("saved-job"));
        Job ignoredJob = jobRepository.save(createJob("ignored-job"));

        userJobService.markViewed(user.getId(), viewedJob.getId());
        userJobService.saveJob(user.getId(), savedJob.getId());
        userJobService.ignoreJob(user.getId(), ignoredJob.getId());

        List<UserJobResponse> viewedJobs = userJobService.getMyViewedJobs(user.getId());
        List<UserJobResponse> savedJobs = userJobService.getMySavedJobs(user.getId());
        List<UserJobResponse> ignoredJobs = userJobService.getMyIgnoredJobs(user.getId());

        assertThat(viewedJobs)
                .extracting(UserJobResponse::status)
                .containsExactly(UserJobStatus.VIEWED);
        assertThat(savedJobs)
                .extracting(UserJobResponse::status)
                .containsExactly(UserJobStatus.SAVED);
        assertThat(ignoredJobs)
                .extracting(UserJobResponse::status)
                .containsExactly(UserJobStatus.IGNORED);
    }

    @Test
    @DisplayName("같은 사용자와 공고에는 UserJob row를 하나만 유지한다")
    void keepSingleUserJobPerUserAndJob() {
        User user = userRepository.save(User.signup("user@example.com", "encoded-password", "사용자"));
        Job job = jobRepository.save(createJob("job-1"));

        userJobService.markViewed(user.getId(), job.getId());
        userJobService.saveJob(user.getId(), job.getId());
        userJobService.ignoreJob(user.getId(), job.getId());
        userJobService.markViewed(user.getId(), job.getId());

        List<UserJob> userJobs = userJobRepository.findAll();

        assertThat(userJobs).hasSize(1);
        assertThat(userJobs.getFirst().getStatus()).isEqualTo(UserJobStatus.VIEWED);
        assertThat(userJobs.getFirst().getViewedAt()).isNotNull();
        assertThat(userJobs.getFirst().getSavedAt()).isNotNull();
        assertThat(userJobs.getFirst().getIgnoredAt()).isNotNull();
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
