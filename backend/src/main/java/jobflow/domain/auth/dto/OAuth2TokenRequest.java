package jobflow.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuth2TokenRequest(

        @NotBlank(message = "OAuth2 인증 코드는 필수입니다.")
        String code
) {
}
