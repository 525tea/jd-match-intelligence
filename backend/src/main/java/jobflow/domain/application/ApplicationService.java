package jobflow.domain.application;

import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.application.dto.ApplicationCreateRequest;
import jobflow.domain.application.dto.ApplicationResponse;
import jobflow.domain.application.dto.ApplicationStatusUpdateRequest;
import jobflow.domain.application.dto.ApplicationSummaryResponse;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobStatus;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.ConflictException;
import jobflow.global.error.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jobflow.domain.outbox.OutboxEvent;
import jobflow.domain.outbox.OutboxEventService;
import jobflow.domain.outbox.OutboxEventTypes;
import jobflow.domain.outbox.payload.ApplicationOutboxPayload;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final OutboxEventService outboxEventService;
    private final ApplicationStatusHistoryRepository applicationStatusHistoryRepository;

    @Transactional
    public ApplicationResponse createApplication(
            Long userId,
            ApplicationCreateRequest request
    ) {
        User user = findUser(userId);
        Job job = findJob(request.jobId());

        validateJobOpen(job);
        validateNotApplied(userId, request.jobId());

        Application application = Application.create(user, job);

        try {
            Application savedApplication = applicationRepository.saveAndFlush(application);
            applicationStatusHistoryRepository.save(ApplicationStatusHistory.record(
                    savedApplication,
                    null,
                    savedApplication.getStatus(),
                    savedApplication.getAppliedAt()
            ));

            outboxEventService.save(
                    "APPLICATION",
                    savedApplication.getId(),
                    OutboxEventTypes.APPLICATION_CREATED,
                    ApplicationOutboxPayload.from(savedApplication),
                    OutboxEvent.TOPIC_APPLICATION_EVENTS
            );

            return ApplicationResponse.from(savedApplication);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(ErrorCode.APPLICATION_ALREADY_EXISTS);
        }
    }

    public ApplicationResponse getApplication(
            Long userId,
            Long applicationId
    ) {
        Application application = findApplicationOfUser(applicationId, userId);

        return ApplicationResponse.from(application);
    }

    public List<ApplicationSummaryResponse> getMyApplications(Long userId) {
        return applicationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(ApplicationSummaryResponse::from)
                .toList();
    }

    @Transactional
    public ApplicationResponse updateApplicationStatus(
            Long userId,
            Long applicationId,
            ApplicationStatusUpdateRequest request
    ) {
        Application application = findApplicationOfUser(applicationId, userId);

        try {
            ApplicationStatus previousStatus = application.changeStatus(request.status());
            if (previousStatus != application.getStatus()) {
                applicationStatusHistoryRepository.save(ApplicationStatusHistory.record(
                        application,
                        previousStatus,
                        application.getStatus(),
                        LocalDateTime.now()
                ));
            }
            applicationRepository.flush();

            outboxEventService.save(
                    "APPLICATION",
                    application.getId(),
                    OutboxEventTypes.APPLICATION_STATUS_CHANGED,
                    ApplicationOutboxPayload.from(application),
                    OutboxEvent.TOPIC_APPLICATION_EVENTS
            );

            return ApplicationResponse.from(application);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ConflictException(ErrorCode.APPLICATION_STATUS_CONFLICT);
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    private Job findJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.JOB_NOT_FOUND));
    }

    private Application findApplicationOfUser(Long applicationId, Long userId) {
        return applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.APPLICATION_NOT_FOUND));
    }

    private void validateJobOpen(Job job) {
        if (job.getStatus() != JobStatus.OPEN) {
            throw new ConflictException(ErrorCode.APPLICATION_STATUS_CONFLICT);
        }
    }

    private void validateNotApplied(Long userId, Long jobId) {
        if (applicationRepository.existsByUserIdAndJobId(userId, jobId)) {
            throw new ConflictException(ErrorCode.APPLICATION_ALREADY_EXISTS);
        }
    }
}
