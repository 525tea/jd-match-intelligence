package jobflow.domain.userjob;

import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.domain.userjob.dto.UserJobResponse;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserJobService {

    private final UserJobRepository userJobRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UserJobResponse markViewed(Long userId, Long jobId) {
        LocalDateTime now = LocalDateTime.now();

        UserJob userJob = userJobRepository.findByUserIdAndJobId(userId, jobId)
                .map(existingUserJob -> {
                    existingUserJob.markViewed(now);
                    return existingUserJob;
                })
                .orElseGet(() -> createViewedUserJob(userId, jobId, now));

        publishUserJobChangedEvent(userJob);
        return UserJobResponse.from(userJob);
    }

    @Transactional
    public UserJobResponse saveJob(Long userId, Long jobId) {
        UserJob userJob = findOrCreateViewedUserJob(userId, jobId);

        userJob.save(LocalDateTime.now());

        publishUserJobChangedEvent(userJob);
        return UserJobResponse.from(userJob);
    }

    @Transactional
    public UserJobResponse ignoreJob(Long userId, Long jobId) {
        UserJob userJob = findOrCreateViewedUserJob(userId, jobId);

        userJob.ignore(LocalDateTime.now());

        publishUserJobChangedEvent(userJob);
        return UserJobResponse.from(userJob);
    }

    @Transactional
    public UserJobResponse unsaveJob(Long userId, Long jobId) {
        UserJob userJob = findUserJob(userId, jobId);

        userJob.unsave(LocalDateTime.now());

        publishUserJobChangedEvent(userJob);
        return UserJobResponse.from(userJob);
    }

    @Transactional
    public UserJobResponse unignoreJob(Long userId, Long jobId) {
        UserJob userJob = findUserJob(userId, jobId);

        userJob.unignore(LocalDateTime.now());

        publishUserJobChangedEvent(userJob);
        return UserJobResponse.from(userJob);
    }

    public UserJobResponse getMyJob(Long userId, Long jobId) {
        UserJob userJob = userJobRepository.findByUserIdAndJobId(userId, jobId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_JOB_NOT_FOUND));

        return UserJobResponse.from(userJob);
    }

    public List<UserJobResponse> getMyViewedJobs(Long userId) {
        return getMyViewedJobs(userId, 0, 20);
    }

    public List<UserJobResponse> getMySavedJobs(Long userId) {
        return getMySavedJobs(userId, 0, 20);
    }

    public List<UserJobResponse> getMyIgnoredJobs(Long userId) {
        return getMyIgnoredJobs(userId, 0, 20);
    }

    public List<UserJobResponse> getMyViewedJobs(Long userId, int page, int size) {
        return getMyJobsByStatus(userId, UserJobStatus.VIEWED, page, size);
    }

    public List<UserJobResponse> getMySavedJobs(Long userId, int page, int size) {
        return getMyJobsByStatus(userId, UserJobStatus.SAVED, page, size);
    }

    public List<UserJobResponse> getMyIgnoredJobs(Long userId, int page, int size) {
        return getMyJobsByStatus(userId, UserJobStatus.IGNORED, page, size);
    }

    private List<UserJobResponse> getMyJobsByStatus(Long userId, UserJobStatus status, int page, int size) {
        return userJobRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(
                        userId,
                        status,
                        PageRequest.of(normalizePage(page), normalizeSize(size))
                )
                .stream()
                .map(UserJobResponse::from)
                .toList();
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return 20;
        }

        return Math.min(size, 100);
    }

    private UserJob findUserJob(Long userId, Long jobId) {
        return userJobRepository.findByUserIdAndJobId(userId, jobId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_JOB_NOT_FOUND));
    }

    private UserJob findOrCreateViewedUserJob(Long userId, Long jobId) {
        return userJobRepository.findByUserIdAndJobId(userId, jobId)
                .orElseGet(() -> createViewedUserJob(userId, jobId, LocalDateTime.now()));
    }

    private UserJob createViewedUserJob(Long userId, Long jobId, LocalDateTime viewedAt) {
        User user = findUser(userId);
        Job job = findJob(jobId);
        UserJob userJob = UserJob.viewed(user, job, viewedAt);

        return userJobRepository.save(userJob);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    private Job findJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.JOB_NOT_FOUND));
    }

    private void publishUserJobChangedEvent(UserJob userJob) {
        eventPublisher.publishEvent(new UserJobChangedEvent(
                userJob.getUser().getId(),
                userJob.getJob().getId(),
                userJob.getStatus()
        ));
    }
}
