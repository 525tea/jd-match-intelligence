package jobflow.domain.userjob;

import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.domain.userjob.dto.UserJobResponse;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserJobService {

    private final UserJobRepository userJobRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;

    @Transactional
    public UserJobResponse markViewed(Long userId, Long jobId) {
        LocalDateTime now = LocalDateTime.now();

        UserJob userJob = userJobRepository.findByUserIdAndJobId(userId, jobId)
                .map(existingUserJob -> {
                    existingUserJob.markViewed(now);
                    return existingUserJob;
                })
                .orElseGet(() -> createViewedUserJob(userId, jobId, now));

        return UserJobResponse.from(userJob);
    }

    @Transactional
    public UserJobResponse saveJob(Long userId, Long jobId) {
        UserJob userJob = findOrCreateViewedUserJob(userId, jobId);

        userJob.save(LocalDateTime.now());

        return UserJobResponse.from(userJob);
    }

    @Transactional
    public UserJobResponse ignoreJob(Long userId, Long jobId) {
        UserJob userJob = findOrCreateViewedUserJob(userId, jobId);

        userJob.ignore(LocalDateTime.now());

        return UserJobResponse.from(userJob);
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
}
