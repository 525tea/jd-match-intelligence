package jobflow.domain.auth.dto;

public record DemoLoginResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        Long userProjectId
) {

    public static DemoLoginResponse bearer(String accessToken, long expiresIn, Long userProjectId) {
        return new DemoLoginResponse("Bearer", accessToken, expiresIn, userProjectId);
    }
}
