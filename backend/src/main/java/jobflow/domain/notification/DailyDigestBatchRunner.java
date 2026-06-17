package jobflow.domain.notification;

import java.util.Arrays;
import java.util.List;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "jobflow.notification.daily-digest.runner",
        name = "enabled",
        havingValue = "true"
)
public class DailyDigestBatchRunner implements ApplicationRunner {

    private static final String MODE_RETRY = "retry";

    private final DailyDigestBatchService batchService;

    @Value("${jobflow.notification.daily-digest.runner.mode:daily}")
    private String mode;

    @Value("${jobflow.notification.daily-digest.runner.target-roles:}")
    private String targetRoles;

    @Value("${jobflow.notification.daily-digest.runner.target-career-level:}")
    private String targetCareerLevel;

    @Override
    public void run(ApplicationArguments args) {
        List<JobRole> roles = parseTargetRoles(targetRoles);
        CareerLevel careerLevel = parseTargetCareerLevel(targetCareerLevel);

        DailyDigestBatchResult result = MODE_RETRY.equalsIgnoreCase(mode)
                ? batchService.retryPendingDailyDigests(roles, careerLevel)
                : batchService.sendDailyDigests(roles, careerLevel);

        log.info(
                "Daily digest batch runner completed. mode={}, targetRoles={}, targetCareerLevel={}, "
                        + "targetCount={}, sentCount={}, failedCount={}, skippedCount={}",
                mode,
                roles,
                careerLevel,
                result.targetCount(),
                result.sentCount(),
                result.failedCount(),
                result.skippedCount()
        );
    }

    private List<JobRole> parseTargetRoles(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(JobRole::valueOf)
                .toList();
    }

    private CareerLevel parseTargetCareerLevel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return CareerLevel.valueOf(value.trim());
    }
}
