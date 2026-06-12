package jobflow.global.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import jobflow.domain.auth.OAuth2AuthService;
import jobflow.domain.auth.oauth.OAuth2UserInfo;
import jobflow.domain.auth.oauth.OAuth2UserInfoFactory;
import jobflow.domain.auth.oauth.code.OAuth2AuthorizationCode;
import jobflow.domain.auth.oauth.code.OAuth2AuthorizationCodeStore;
import jobflow.domain.auth.oauth.token.OAuth2ProviderTokenCommand;
import jobflow.domain.auth.oauth.token.OAuth2ProviderTokenService;
import jobflow.domain.user.User;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2AuthService oAuth2AuthService;
    private final OAuth2AuthorizationCodeStore authorizationCodeStore;
    private final OAuth2Properties oAuth2Properties;
    private final ObjectProvider<OAuth2AuthorizedClientService> authorizedClientServiceProvider;
    private final OAuth2ProviderTokenService providerTokenService;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauth2User = oauthToken.getPrincipal();

        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.of(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauth2User.getAttributes()
        );

        User user = oAuth2AuthService.findOrCreateUser(userInfo);
        saveProviderAccessToken(user, userInfo, oauthToken);

        OAuth2AuthorizationCode authorizationCode = authorizationCodeStore.save(user.getId());

        String redirectUri = UriComponentsBuilder
                .fromUriString(oAuth2Properties.successRedirectUri())
                .queryParam("code", authorizationCode.code())
                .build()
                .toUriString();

        response.sendRedirect(redirectUri);
    }

    private void saveProviderAccessToken(
            User user,
            OAuth2UserInfo userInfo,
            OAuth2AuthenticationToken oauthToken
    ) {
        OAuth2AuthorizedClientService authorizedClientService = authorizedClientServiceProvider.getIfAvailable();
        if (authorizedClientService == null) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_INVALID);
        }

        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName()
        );

        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_INVALID);
        }

        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();

        providerTokenService.saveOrReplace(new OAuth2ProviderTokenCommand(
                user.getId(),
                userInfo.getProvider(),
                accessToken.getTokenValue(),
                accessToken.getTokenType().getValue(),
                accessToken.getScopes(),
                toLocalDateTime(accessToken.getIssuedAt()),
                toLocalDateTime(accessToken.getExpiresAt())
        ));
    }

    private LocalDateTime toLocalDateTime(java.time.Instant instant) {
        if (instant == null) {
            return null;
        }

        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
