package jobflow.domain.application;

import jobflow.domain.application.dto.ApplicationCreateRequest;
import jobflow.domain.application.dto.ApplicationResponse;
import jobflow.domain.application.dto.ApplicationStatusUpdateRequest;
import jobflow.domain.application.dto.ApplicationSummaryResponse;
import jobflow.domain.job.*;
import jobflow.domain.outbox.OutboxEventService;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.ConflictException;
import jobflow.global.error.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @InjectMocks
    private ApplicationService applicationService;

    @Test
    @DisplayName("지원 상태를 생성한다")
    void createApplication() {
        Long userId = 1L;
        Long jobId = 10L;
        User user = createUser(userId);
        Job job = createJob(jobId);
        Application savedApplication = createApplicationEntity(100L, user, job);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(jobRepository.findById(jobId)).willReturn(Optional.of(job));
        given(applicationRepository.existsByUserIdAndJobId(userId, jobId)).willReturn(false);
        given(applicationRepository.saveAndFlush(any(Application.class))).willReturn(savedApplication);

        ApplicationResponse response = applicationService.createApplication(
                userId,
                new ApplicationCreateRequest(jobId)
        );

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.jobId()).isEqualTo(jobId);
        assertThat(response.jobTitle()).isEqualTo("백엔드 개발자");
        assertThat(response.companyName()).isEqualTo("JobFlow");
        assertThat(response.status()).isEqualTo(ApplicationStatus.APPLIED);

        verify(applicationRepository).saveAndFlush(any(Application.class));
        verify(outboxEventService).save(
                eq("APPLICATION"),
                eq(100L),
                eq("APPLICATION_CREATED"),
                any(),
                eq("application.events")
        );
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 지원 상태를 생성하면 예외가 발생한다")
    void createApplicationWithMissingUser() {
        Long userId = 999L;

        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.createApplication(
                userId,
                new ApplicationCreateRequest(10L)
        ))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 공고에 지원 상태를 생성하면 예외가 발생한다")
    void createApplicationWithMissingJob() {
        Long userId = 1L;
        Long jobId = 999L;
        User user = createUser(userId);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(jobRepository.findById(jobId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.createApplication(
                userId,
                new ApplicationCreateRequest(jobId)
        ))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.JOB_NOT_FOUND);
    }

    @Test
    @DisplayName("마감된 공고에는 지원 상태를 생성할 수 없다")
    void createApplicationWithClosedJob() {
        Long userId = 1L;
        Long jobId = 10L;
        User user = createUser(userId);
        Job job = createJob(jobId);
        job.close();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(jobRepository.findById(jobId)).willReturn(Optional.of(job));

        assertThatThrownBy(() -> applicationService.createApplication(
                userId,
                new ApplicationCreateRequest(jobId)
        ))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPLICATION_STATUS_CONFLICT);
    }

    @Test
    @DisplayName("이미 지원한 공고에는 중복 지원 상태를 생성할 수 없다")
    void createDuplicatedApplication() {
        Long userId = 1L;
        Long jobId = 10L;
        User user = createUser(userId);
        Job job = createJob(jobId);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(jobRepository.findById(jobId)).willReturn(Optional.of(job));
        given(applicationRepository.existsByUserIdAndJobId(userId, jobId)).willReturn(true);

        assertThatThrownBy(() -> applicationService.createApplication(
                userId,
                new ApplicationCreateRequest(jobId)
        ))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPLICATION_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("DB unique 제약 충돌 시 중복 지원 예외로 변환한다")
    void createApplicationWithUniqueConstraintViolation() {
        Long userId = 1L;
        Long jobId = 10L;
        User user = createUser(userId);
        Job job = createJob(jobId);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(jobRepository.findById(jobId)).willReturn(Optional.of(job));
        given(applicationRepository.existsByUserIdAndJobId(userId, jobId)).willReturn(false);
        given(applicationRepository.saveAndFlush(any(Application.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> applicationService.createApplication(
                userId,
                new ApplicationCreateRequest(jobId)
        ))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPLICATION_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("내 지원 상태를 단건 조회한다")
    void getApplication() {
        Long userId = 1L;
        Long applicationId = 100L;
        User user = createUser(userId);
        Job job = createJob(10L);
        Application application = createApplicationEntity(applicationId, user, job);

        given(applicationRepository.findByIdAndUserId(applicationId, userId))
                .willReturn(Optional.of(application));

        ApplicationResponse response = applicationService.getApplication(userId, applicationId);

        assertThat(response.id()).isEqualTo(applicationId);
        assertThat(response.jobId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    @DisplayName("다른 사용자의 지원 상태는 조회할 수 없다")
    void getApplicationOfOtherUser() {
        Long userId = 1L;
        Long applicationId = 100L;

        given(applicationRepository.findByIdAndUserId(applicationId, userId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.getApplication(userId, applicationId))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("내 지원 상태 목록을 조회한다")
    void getMyApplications() {
        Long userId = 1L;
        User user = createUser(userId);
        Job job = createJob(10L);
        Application application = createApplicationEntity(100L, user, job);

        given(applicationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .willReturn(List.of(application));

        List<ApplicationSummaryResponse> responses = applicationService.getMyApplications(userId);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(100L);
        assertThat(responses.getFirst().jobId()).isEqualTo(10L);
        assertThat(responses.getFirst().status()).isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    @DisplayName("내 지원 상태를 변경한다")
    void updateApplicationStatus() {
        Long userId = 1L;
        Long applicationId = 100L;
        User user = createUser(userId);
        Job job = createJob(10L);
        Application application = createApplicationEntity(applicationId, user, job);

        given(applicationRepository.findByIdAndUserId(applicationId, userId))
                .willReturn(Optional.of(application));

        ApplicationResponse response = applicationService.updateApplicationStatus(
                userId,
                applicationId,
                new ApplicationStatusUpdateRequest(ApplicationStatus.INTERVIEW)
        );

        assertThat(response.status()).isEqualTo(ApplicationStatus.INTERVIEW);

        verify(applicationRepository).flush();
        verify(outboxEventService).save(
                eq("APPLICATION"),
                eq(applicationId),
                eq("APPLICATION_STATUS_CHANGED"),
                any(),
                eq("application.events")
        );
    }

    @Test
    @DisplayName("종료 상태 이후에는 추가 상태 변경을 할 수 없다")
    void updateTerminalApplicationStatus() {
        Long userId = 1L;
        Long applicationId = 100L;
        User user = createUser(userId);
        Job job = createJob(10L);
        Application application = createApplicationEntity(applicationId, user, job);
        application.changeStatus(ApplicationStatus.REJECTED);

        given(applicationRepository.findByIdAndUserId(applicationId, userId))
                .willReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.updateApplicationStatus(
                userId,
                applicationId,
                new ApplicationStatusUpdateRequest(ApplicationStatus.INTERVIEW)
        ))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPLICATION_STATUS_CONFLICT);
    }

    @Test
    @DisplayName("상태 변경 중 optimistic lock 충돌이 발생하면 409 예외로 변환한다")
    void updateApplicationStatusWithOptimisticLockConflict() {
        Long userId = 1L;
        Long applicationId = 100L;
        User user = createUser(userId);
        Job job = createJob(10L);
        Application application = createApplicationEntity(applicationId, user, job);

        given(applicationRepository.findByIdAndUserId(applicationId, userId))
                .willReturn(Optional.of(application));
        willThrow(new ObjectOptimisticLockingFailureException(Application.class, applicationId))
                .given(applicationRepository)
                .flush();

        assertThatThrownBy(() -> applicationService.updateApplicationStatus(
                userId,
                applicationId,
                new ApplicationStatusUpdateRequest(ApplicationStatus.INTERVIEW)
        ))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPLICATION_STATUS_CONFLICT);
        verify(outboxEventService, never()).save(any(), any(), any(), any(), any());
    }

    private User createUser(Long id) {
        User user = User.signup("test@example.com", "encoded-password", "테스트");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Job createJob(Long id) {
        Job job = Job.create(
                "MANUAL",
                "external-1",
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 백엔드 개발자를 채용합니다.",
                "https://example.com/jobs/1",
                JobRole.BACKEND,
                "Java/Spring",
                CareerLevel.JUNIOR,
                1,
                3,
                "학력무관",
                EmploymentType.FULL_TIME,
                "STARTUP",
                "IT",
                "KR",
                "Seoul",
                "Gangnam",
                RemoteType.HYBRID,
                4000,
                7000,
                "KRW",
                true,
                1,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(14)
        );

        ReflectionTestUtils.setField(job, "id", id);
        return job;
    }

    private Application createApplicationEntity(Long id, User user, Job job) {
        Application application = Application.create(user, job);
        ReflectionTestUtils.setField(application, "id", id);
        ReflectionTestUtils.setField(application, "version", 0L);
        return application;
    }
}
