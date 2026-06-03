package jobflow.domain.userjob;

import jobflow.domain.job.*;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.domain.userjob.dto.UserJobResponse;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserJobServiceTest {

    @Mock
    private UserJobRepository userJobRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private UserJobService userJobService;

    @Test
    @DisplayName("조회 이력이 없으면 VIEWED 상태 UserJob을 생성한다")
    void markViewedCreatesUserJob() {
        Long userId = 1L;
        Long jobId = 10L;
        User user = createUser(userId);
        Job job = createJob(jobId);

        given(userJobRepository.findByUserIdAndJobId(userId, jobId)).willReturn(Optional.empty());
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(jobRepository.findById(jobId)).willReturn(Optional.of(job));
        given(userJobRepository.save(any(UserJob.class)))
                .willAnswer(invocation -> {
                    UserJob userJob = invocation.getArgument(0);
                    ReflectionTestUtils.setField(userJob, "id", 100L);
                    return userJob;
                });

        UserJobResponse response = userJobService.markViewed(userId, jobId);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.jobId()).isEqualTo(jobId);
        assertThat(response.status()).isEqualTo(UserJobStatus.VIEWED);
        assertThat(response.viewedAt()).isNotNull();

        verify(userJobRepository).save(any(UserJob.class));
    }

    @Test
    @DisplayName("기존 UserJob이 있으면 VIEWED 상태로 갱신한다")
    void markViewedUpdatesExistingUserJob() {
        Long userId = 1L;
        Long jobId = 10L;
        UserJob userJob = createUserJob(100L, userId, jobId);
        userJob.save(LocalDateTime.of(2026, 6, 4, 10, 0));

        given(userJobRepository.findByUserIdAndJobId(userId, jobId)).willReturn(Optional.of(userJob));

        UserJobResponse response = userJobService.markViewed(userId, jobId);

        assertThat(response.status()).isEqualTo(UserJobStatus.VIEWED);
        assertThat(response.viewedAt()).isNotNull();

        verify(userJobRepository, never()).save(any(UserJob.class));
    }

    @Test
    @DisplayName("공고를 SAVED 상태로 변경한다")
    void saveJob() {
        Long userId = 1L;
        Long jobId = 10L;
        UserJob userJob = createUserJob(100L, userId, jobId);

        given(userJobRepository.findByUserIdAndJobId(userId, jobId)).willReturn(Optional.of(userJob));

        UserJobResponse response = userJobService.saveJob(userId, jobId);

        assertThat(response.status()).isEqualTo(UserJobStatus.SAVED);
        assertThat(response.savedAt()).isNotNull();
        assertThat(response.ignoredAt()).isNull();

        verify(userJobRepository, never()).save(any(UserJob.class));
    }

    @Test
    @DisplayName("공고를 IGNORED 상태로 변경한다")
    void ignoreJob() {
        Long userId = 1L;
        Long jobId = 10L;
        UserJob userJob = createUserJob(100L, userId, jobId);

        given(userJobRepository.findByUserIdAndJobId(userId, jobId)).willReturn(Optional.of(userJob));

        UserJobResponse response = userJobService.ignoreJob(userId, jobId);

        assertThat(response.status()).isEqualTo(UserJobStatus.IGNORED);
        assertThat(response.ignoredAt()).isNotNull();
        assertThat(response.savedAt()).isNull();

        verify(userJobRepository, never()).save(any(UserJob.class));
    }

    @Test
    @DisplayName("UserJob이 없는 공고를 저장하면 조회 상태를 만든 뒤 SAVED로 변경한다")
    void saveJobCreatesUserJobWhenMissing() {
        Long userId = 1L;
        Long jobId = 10L;
        User user = createUser(userId);
        Job job = createJob(jobId);

        given(userJobRepository.findByUserIdAndJobId(userId, jobId)).willReturn(Optional.empty());
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(jobRepository.findById(jobId)).willReturn(Optional.of(job));
        given(userJobRepository.save(any(UserJob.class)))
                .willAnswer(invocation -> {
                    UserJob userJob = invocation.getArgument(0);
                    ReflectionTestUtils.setField(userJob, "id", 100L);
                    return userJob;
                });

        UserJobResponse response = userJobService.saveJob(userId, jobId);

        assertThat(response.status()).isEqualTo(UserJobStatus.SAVED);
        assertThat(response.viewedAt()).isNotNull();
        assertThat(response.savedAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 예외가 발생한다")
    void failWhenUserNotFound() {
        Long userId = 999L;
        Long jobId = 10L;

        given(userJobRepository.findByUserIdAndJobId(userId, jobId)).willReturn(Optional.empty());
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userJobService.markViewed(userId, jobId))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 공고면 예외가 발생한다")
    void failWhenJobNotFound() {
        Long userId = 1L;
        Long jobId = 999L;
        User user = createUser(userId);

        given(userJobRepository.findByUserIdAndJobId(userId, jobId)).willReturn(Optional.empty());
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(jobRepository.findById(jobId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userJobService.markViewed(userId, jobId))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.JOB_NOT_FOUND);
    }

    private UserJob createUserJob(Long userJobId, Long userId, Long jobId) {
        UserJob userJob = UserJob.viewed(
                createUser(userId),
                createJob(jobId),
                LocalDateTime.of(2026, 6, 4, 9, 0)
        );
        ReflectionTestUtils.setField(userJob, "id", userJobId);

        return userJob;
    }

    private User createUser(Long id) {
        User user = User.signup("user" + id + "@example.com", "encoded-password", "사용자");
        ReflectionTestUtils.setField(user, "id", id);

        return user;
    }

    private Job createJob(Long id) {
        Job job = Job.create(
                "MANUAL",
                "job-" + id,
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 백엔드 개발자 채용",
                "https://example.com/jobs/" + id,
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
        ReflectionTestUtils.setField(job, "id", id);

        return job;
    }

    @Test
    @DisplayName("내 저장 공고 목록을 조회한다")
    void getMySavedJobs() {
        Long userId = 1L;
        UserJob userJob = createUserJob(100L, userId, 10L);
        userJob.save(LocalDateTime.of(2026, 6, 4, 11, 0));

        given(userJobRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, UserJobStatus.SAVED))
                .willReturn(List.of(userJob));

        List<UserJobResponse> responses = userJobService.getMySavedJobs(userId);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(100L);
        assertThat(responses.getFirst().status()).isEqualTo(UserJobStatus.SAVED);
    }

    @Test
    @DisplayName("내 무시 공고 목록을 조회한다")
    void getMyIgnoredJobs() {
        Long userId = 1L;
        UserJob userJob = createUserJob(100L, userId, 10L);
        userJob.ignore(LocalDateTime.of(2026, 6, 4, 11, 0));

        given(userJobRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, UserJobStatus.IGNORED))
                .willReturn(List.of(userJob));

        List<UserJobResponse> responses = userJobService.getMyIgnoredJobs(userId);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().status()).isEqualTo(UserJobStatus.IGNORED);
    }

    @Test
    @DisplayName("내 조회 공고 목록을 조회한다")
    void getMyViewedJobs() {
        Long userId = 1L;
        UserJob userJob = createUserJob(100L, userId, 10L);

        given(userJobRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, UserJobStatus.VIEWED))
                .willReturn(List.of(userJob));

        List<UserJobResponse> responses = userJobService.getMyViewedJobs(userId);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().status()).isEqualTo(UserJobStatus.VIEWED);
    }
}
