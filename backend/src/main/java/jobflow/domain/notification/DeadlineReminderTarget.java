package jobflow.domain.notification;

import java.time.LocalDateTime;

public record DeadlineReminderTarget(
        Long userId,
        String userEmail,
        String userName,
        Long jobId,
        String jobTitle,
        String companyName,
        LocalDateTime deadlineAt,
        String originalUrl
) {
}
