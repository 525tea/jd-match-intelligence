package jobflow.domain.auth.dto;

public record MeResponse(
        Long id,
        String email,
        String name,
        String role
) {
}
