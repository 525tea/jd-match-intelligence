package jobflow.domain.auth.oauth;

import jobflow.domain.user.AuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class OAuth2UserEmailResolver {

    private final GitHubOAuth2EmailClient gitHubEmailClient;

    public OAuth2UserInfo resolve(OAuth2UserInfo userInfo, OAuth2AuthorizedClient authorizedClient) {
        if (StringUtils.hasText(userInfo.getEmail())) {
            return userInfo;
        }

        if (userInfo.getProvider() != AuthProvider.GITHUB) {
            return userInfo;
        }

        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            return userInfo;
        }

        return gitHubEmailClient.fetchPrimaryVerifiedEmail(authorizedClient.getAccessToken().getTokenValue())
                .<OAuth2UserInfo>map(email -> ResolvedOAuth2UserInfo.from(userInfo, email))
                .orElse(userInfo);
    }
}
