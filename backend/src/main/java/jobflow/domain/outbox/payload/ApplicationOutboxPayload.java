package jobflow.domain.outbox.payload;

import jobflow.domain.application.Application;
import jobflow.domain.application.ApplicationStatus;

public record ApplicationOutboxPayload(
        Long applicationId,
        Long userId,
        Long jobId,
        ApplicationStatus status
) {

    public static ApplicationOutboxPayload from(Application application) {
        return new ApplicationOutboxPayload(
                application.getId(),
                application.getUser().getId(),
                application.getJob().getId(),
                application.getStatus()
        );
    }
}
