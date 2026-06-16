package jobflow.domain.notification;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import jobflow.domain.notification.digest.DailyDigestContent;
import jobflow.domain.notification.digest.DailyDigestContentService;
import jobflow.domain.notification.digest.DailyDigestEmailRenderer;
import jobflow.domain.project.UserProject;
import jobflow.domain.project.UserProjectRepository;
import jobflow.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyDigestBatchService {

    private static final NotificationType TYPE = NotificationType.DAILY_DIGEST;

    private final UserProjectRepository userProjectRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationAttemptRepository notificationAttemptRepository;
    private final DailyDigestIdempotencyService idempotencyService;
    private final DailyDigestContentService contentService;
    private final DailyDigestEmailRenderer emailRenderer;
    private final EmailSender emailSender;
    private final DailyDigestProperties properties;
    private final Clock clock;

    @Transactional
    public DailyDigestBatchResult sendDailyDigests(
            Collection<JobRole> targetRoles,
            CareerLevel targetCareerLevel
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate digestDate = now.toLocalDate();
        List<UserProject> targetProjects = findLatestProjectPerUser();

        BatchCounter counter = new BatchCounter(targetProjects.size());
        for (UserProject userProject : targetProjects) {
            sendNewDigest(userProject, targetRoles, targetCareerLevel, digestDate, now, counter);
        }

        DailyDigestBatchResult result = counter.toResult();
        log.info(
                "Daily digest batch completed. targetCount={}, sentCount={}, failedCount={}, skippedCount={}",
                result.targetCount(),
                result.sentCount(),
                result.failedCount(),
                result.skippedCount()
        );
        return result;
    }

    @Transactional
    public DailyDigestBatchResult retryPendingDailyDigests(
            Collection<JobRole> targetRoles,
            CareerLevel targetCareerLevel
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<NotificationLog> retryableLogs =
                notificationLogRepository.findByTypeAndStatusAndNextRetryAtLessThanEqual(
                        TYPE,
                        NotificationStatus.PENDING,
                        now
                );

        BatchCounter counter = new BatchCounter(retryableLogs.size());
        for (NotificationLog notificationLog : retryableLogs) {
            retryExistingDigest(notificationLog, targetRoles, targetCareerLevel, now, counter);
        }
        return counter.toResult();
    }

    private List<UserProject> findLatestProjectPerUser() {
        Map<Long, UserProject> latestProjectByUserId = new LinkedHashMap<>();
        for (UserProject userProject : userProjectRepository.findAllByOrderByUpdatedAtDescIdDesc()) {
            latestProjectByUserId.putIfAbsent(userProject.getUser().getId(), userProject);
        }
        return List.copyOf(latestProjectByUserId.values());
    }

    private void sendNewDigest(
            UserProject userProject,
            Collection<JobRole> targetRoles,
            CareerLevel targetCareerLevel,
            LocalDate digestDate,
            LocalDateTime now,
            BatchCounter counter
    ) {
        User user = userProject.getUser();
        String deduplicationKey = NotificationLog.dailyDigestDeduplicationKey(digestDate);

        if (!idempotencyService.acquire(user.getId(), digestDate)) {
            counter.skipped++;
            return;
        }
        if (notificationLogRepository.existsByUserIdAndTypeAndDeduplicationKey(
                user.getId(),
                TYPE,
                deduplicationKey
        )) {
            counter.skipped++;
            return;
        }

        DailyDigestContent content = contentService.buildDigest(
                user.getId(),
                userProject.getId(),
                targetRoles,
                targetCareerLevel
        );
        if (content.isEmpty()) {
            idempotencyService.release(user.getId(), digestDate);
            counter.skipped++;
            return;
        }

        NotificationLog notificationLog = notificationLogRepository.save(NotificationLog.createDailyDigest(
                user,
                digestDate,
                properties.maxAttempts(),
                now
        ));
        sendExistingLog(notificationLog, userProject, content, now, counter);
    }

    private void retryExistingDigest(
            NotificationLog notificationLog,
            Collection<JobRole> targetRoles,
            CareerLevel targetCareerLevel,
            LocalDateTime now,
            BatchCounter counter
    ) {
        if (!notificationLog.canRetry(now)) {
            counter.skipped++;
            return;
        }

        User user = notificationLog.getUser();
        if (!idempotencyService.acquire(user.getId(), notificationLog.getDeduplicationKey())) {
            counter.skipped++;
            return;
        }

        userProjectRepository.findFirstByUserIdOrderByUpdatedAtDescIdDesc(user.getId())
                .ifPresentOrElse(
                        userProject -> sendExistingLog(
                                notificationLog,
                                userProject,
                                targetRoles,
                                targetCareerLevel,
                                now,
                                counter
                        ),
                        () -> {
                            idempotencyService.release(user.getId(), notificationLog.getDeduplicationKey());
                            counter.skipped++;
                        }
                );
    }

    private void sendExistingLog(
            NotificationLog notificationLog,
            UserProject userProject,
            Collection<JobRole> targetRoles,
            CareerLevel targetCareerLevel,
            LocalDateTime now,
            BatchCounter counter
    ) {
        DailyDigestContent content = contentService.buildDigest(
                notificationLog.getUser().getId(),
                userProject.getId(),
                targetRoles,
                targetCareerLevel
        );
        if (content.isEmpty()) {
            idempotencyService.release(
                    notificationLog.getUser().getId(),
                    notificationLog.getDeduplicationKey()
            );
            counter.skipped++;
            return;
        }

        sendExistingLog(notificationLog, userProject, content, now, counter);
    }

    private void sendExistingLog(
            NotificationLog notificationLog,
            UserProject userProject,
            DailyDigestContent content,
            LocalDateTime now,
            BatchCounter counter
    ) {
        User user = userProject.getUser();
        int attemptNumber = notificationLog.nextAttemptNumber();
        EmailSendResult sendResult = emailSender.send(emailRenderer.render(
                user.getEmail(),
                user.getName(),
                content
        ));

        if (sendResult.success()) {
            notificationAttemptRepository.save(NotificationAttempt.sent(
                    notificationLog,
                    attemptNumber,
                    sendResult.provider(),
                    sendResult.providerMessageId(),
                    now
            ));
            notificationLog.markSent(now);
            counter.sent++;
            return;
        }

        notificationAttemptRepository.save(NotificationAttempt.failed(
                notificationLog,
                attemptNumber,
                sendResult.provider(),
                sendResult.failureReason(),
                now
        ));
        notificationLog.markFailed(now, nextRetryAt(now, attemptNumber));
        idempotencyService.release(user.getId(), notificationLog.getDeduplicationKey());
        counter.failed++;
    }

    private LocalDateTime nextRetryAt(LocalDateTime now, int attemptNumber) {
        long delayMinutes = (long) Math.pow(2, Math.max(0, attemptNumber - 1)) * 10L;
        return now.plusMinutes(delayMinutes);
    }

    private static class BatchCounter {

        private final int targetCount;
        private int sent;
        private int failed;
        private int skipped;

        private BatchCounter(int targetCount) {
            this.targetCount = targetCount;
        }

        private DailyDigestBatchResult toResult() {
            return new DailyDigestBatchResult(targetCount, sent, failed, skipped);
        }
    }
}
