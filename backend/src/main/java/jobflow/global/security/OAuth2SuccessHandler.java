package jobflow.global.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import jobflow.domain.auth.OAuth2AuthService;
import jobflow.domain.auth.oauth.OAuth2UserInfo;
import jobflow.domain.auth.oauth.OAuth2UserInfoFactory;
import jobflow.domain.auth.oauth.code.OAuth2AuthorizationCode;
import jobflow.domain.auth.oauth.code.OAuth2AuthorizationCodeStore;
import jobflow.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
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
        OAuth2AuthorizationCode authorizationCode = authorizationCodeStore.save(user.getId());

        String redirectUri = UriComponentsBuilder
                .fromUriString(oAuth2Properties.successRedirectUri())
                .queryParam("code", authorizationCode.code())
                .build()
                .toUriString();

        response.sendRedirect(redirectUri);
    }
}
