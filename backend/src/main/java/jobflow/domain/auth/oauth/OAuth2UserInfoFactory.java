package jobflow.domain.auth.oauth;

import java.util.Map;
import jobflow.domain.user.AuthProvider;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;

public final class OAuth2UserInfoFactory {

    private OAuth2UserInfoFactory() {
    }

    public static OAuth2UserInfo of(
            String registrationId,
            Map<String, Object> attributes
    ) {
        AuthProvider provider = toAuthProvider(registrationId);

        return switch (provider) {
            case GITHUB -> new GitHubOAuth2UserInfo(attributes);
            case GOOGLE -> new GoogleOAuth2UserInfo(attributes);
            case LOCAL -> throw new BusinessException(ErrorCode.AUTH_OAUTH2_PROVIDER_NOT_SUPPORTED);
        };
    }

    private static AuthProvider toAuthProvider(String registrationId) {
        try {
            return AuthProvider.valueOf(registrationId.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_PROVIDER_NOT_SUPPORTED);
        }
    }
}
