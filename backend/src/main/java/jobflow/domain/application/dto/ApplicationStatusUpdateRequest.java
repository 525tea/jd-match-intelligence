package jobflow.domain.application.dto;

import jakarta.validation.constraints.NotNull;
import jobflow.domain.application.ApplicationStatus;

public record ApplicationStatusUpdateRequest(
        @NotNull(message = "지원 상태는 필수입니다.")
        ApplicationStatus status
) {
}
