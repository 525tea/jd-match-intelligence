package jobflow.domain.auth.dto;

public record LoginResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        Long userProjectId
) {

    public static LoginResponse bearer(String accessToken, long expiresIn) {
        return bearer(accessToken, expiresIn, null);
    }

    public static LoginResponse bearer(String accessToken, long expiresIn, Long userProjectId) {
        return new LoginResponse("Bearer", accessToken, expiresIn, userProjectId);
    }
}
