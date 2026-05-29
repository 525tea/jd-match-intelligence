package jobflow.domain.auth.dto;

public record SignupResponse(
        Long id,
        String email,
        String name
) {
}
