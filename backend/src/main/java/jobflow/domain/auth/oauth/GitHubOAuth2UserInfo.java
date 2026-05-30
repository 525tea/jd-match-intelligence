package jobflow.domain.auth.oauth;

import java.util.Map;
import jobflow.domain.user.AuthProvider;

public class GitHubOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public GitHubOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.GITHUB;
    }

    @Override
    public String getProviderId() {
        Object id = attributes.get("id");
        return id == null ? null : String.valueOf(id);
    }

    @Override
    public String getEmail() {
        Object email = attributes.get("email");
        return email == null ? null : String.valueOf(email);
    }

    @Override
    public String getName() {
        Object name = attributes.get("name");

        if (name != null) {
            return String.valueOf(name);
        }

        Object login = attributes.get("login");
        return login == null ? "GitHub User" : String.valueOf(login);
    }
}
