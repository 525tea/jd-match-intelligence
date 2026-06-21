package jobflow.domain.application.dto;

import java.time.LocalDateTime;
import jobflow.domain.application.ApplicationStatus;
import jobflow.domain.application.ApplicationStatusHistory;

public record ApplicationStatusHistoryResponse(
        Long id,
        Long applicationId,
        ApplicationStatus previousStatus,
        ApplicationStatus nextStatus,
        LocalDateTime changedAt,
        LocalDateTime createdAt
) {

    public static ApplicationStatusHistoryResponse from(ApplicationStatusHistory history) {
        return new ApplicationStatusHistoryResponse(
                history.getId(),
                history.getApplication().getId(),
                history.getPreviousStatus(),
                history.getNextStatus(),
                history.getChangedAt(),
                history.getCreatedAt()
        );
    }
}
