package jobflow.domain.application.dto;

import jakarta.validation.constraints.NotNull;

public record ApplicationCreateRequest(
        @NotNull(message = "공고 ID는 필수입니다.")
        Long jobId
) {
}
