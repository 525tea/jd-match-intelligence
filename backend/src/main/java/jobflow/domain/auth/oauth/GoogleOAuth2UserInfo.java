package jobflow.domain.auth.oauth;

import java.util.Map;
import jobflow.domain.user.AuthProvider;

public class GoogleOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public GoogleOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public String getProviderId() {
        Object sub = attributes.get("sub");
        return sub == null ? null : String.valueOf(sub);
    }

    @Override
    public String getEmail() {
        Object email = attributes.get("email");
        return email == null ? null : String.valueOf(email);
    }

    @Override
    public String getName() {
        Object name = attributes.get("name");
        return name == null ? "Google User" : String.valueOf(name);
    }
}
