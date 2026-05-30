package jobflow.domain.auth.oauth;

import jobflow.domain.user.AuthProvider;

public interface OAuth2UserInfo {

    AuthProvider getProvider();

    String getProviderId();

    String getEmail();

    String getName();
}
