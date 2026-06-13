package jobflow.domain.auth.oauth;

import jobflow.domain.user.AuthProvider;

public record ResolvedOAuth2UserInfo(
        AuthProvider provider,
        String providerId,
        String email,
        String name
) implements OAuth2UserInfo {

    public static ResolvedOAuth2UserInfo from(OAuth2UserInfo userInfo, String email) {
        return new ResolvedOAuth2UserInfo(
                userInfo.getProvider(),
                userInfo.getProviderId(),
                email,
                userInfo.getName()
        );
    }

    @Override
    public AuthProvider getProvider() {
        return provider;
    }

    @Override
    public String getProviderId() {
        return providerId;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public String getName() {
        return name;
    }
}
