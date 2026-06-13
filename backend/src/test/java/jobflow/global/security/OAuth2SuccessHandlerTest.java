package jobflow.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jobflow.domain.auth.OAuth2AuthService;
import jobflow.domain.auth.oauth.code.OAuth2AuthorizationCode;
import jobflow.domain.auth.oauth.code.OAuth2AuthorizationCodeStore;
import jobflow.domain.auth.oauth.OAuth2UserEmailResolver;
import jobflow.domain.auth.oauth.OAuth2UserInfo;
import jobflow.domain.auth.oauth.ResolvedOAuth2UserInfo;
import jobflow.domain.auth.oauth.token.OAuth2ProviderTokenCommand;
import jobflow.domain.auth.oauth.token.OAuth2ProviderTokenService;
import jobflow.domain.user.AuthProvider;
import jobflow.domain.user.User;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    @Mock
    private OAuth2AuthService oAuth2AuthService;

    @Mock
    private OAuth2AuthorizationCodeStore authorizationCodeStore;

    @Mock
    private OAuth2Properties oAuth2Properties;

    @Mock
    private ObjectProvider<OAuth2AuthorizedClientService> authorizedClientServiceProvider;

    @Mock
    private OAuth2AuthorizedClientService authorizedClientService;

    @Mock
    private OAuth2ProviderTokenService providerTokenService;

    @Mock
    private OAuth2UserEmailResolver userEmailResolver;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private OAuth2SuccessHandler successHandler;

    @BeforeEach
    void setUp() {
        successHandler = new OAuth2SuccessHandler(
                oAuth2AuthService,
                authorizationCodeStore,
                oAuth2Properties,
                authorizedClientServiceProvider,
                providerTokenService,
                userEmailResolver
        );
    }

    @Test
    @DisplayName("GitHub OAuth2 로그인 성공 시 provider access token을 저장하고 authorization code로 redirect한다")
    void onAuthenticationSuccess() throws Exception {
        User user = User.oauth2(
                "octocat@example.com",
                "octocat",
                AuthProvider.GITHUB,
                "12345"
        );
        ReflectionTestUtils.setField(user, "id", 1L);

        OAuth2AuthenticationToken authentication = githubAuthentication();
        OAuth2AuthorizedClient authorizedClient = githubAuthorizedClient();

        given(oAuth2AuthService.findOrCreateUser(any())).willReturn(user);
        given(authorizedClientServiceProvider.getIfAvailable()).willReturn(authorizedClientService);
        given(authorizedClientService.loadAuthorizedClient("github", authentication.getName()))
                .willReturn(authorizedClient);
        given(userEmailResolver.resolve(any(), any()))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(authorizationCodeStore.save(1L))
                .willReturn(new OAuth2AuthorizationCode(
                        "oauth2-authorization-code",
                        1L,
                        Instant.parse("2026-06-12T10:05:00Z")
                ));
        given(oAuth2Properties.successRedirectUri())
                .willReturn("http://localhost:3000/oauth2/success");

        successHandler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<OAuth2ProviderTokenCommand> commandCaptor =
                ArgumentCaptor.forClass(OAuth2ProviderTokenCommand.class);

        verify(providerTokenService).saveOrReplace(commandCaptor.capture());

        OAuth2ProviderTokenCommand command = commandCaptor.getValue();
        assertThat(command.userId()).isEqualTo(1L);
        assertThat(command.authProvider()).isEqualTo(AuthProvider.GITHUB);
        assertThat(command.accessToken()).isEqualTo("github-provider-access-token");
        assertThat(command.tokenType()).isEqualTo("Bearer");
        assertThat(command.normalizedScopes()).isEqualTo("read:user repo");
        assertThat(command.issuedAt()).isEqualTo(LocalDateTime.of(2026, 6, 12, 10, 0));
        assertThat(command.expiresAt()).isEqualTo(LocalDateTime.of(2026, 6, 12, 11, 0));

        verify(userEmailResolver).resolve(any(), any());
        verify(authorizationCodeStore).save(1L);
        verify(response).sendRedirect("http://localhost:3000/oauth2/success?code=oauth2-authorization-code");
    }

    @Test
    @DisplayName("GitHub profile email이 비공개이면 email resolver로 보정한 뒤 사용자를 생성한다")
    void onAuthenticationSuccessWithPrivateGitHubEmail() throws Exception {
        User user = User.oauth2(
                "primary@example.com",
                "octocat",
                AuthProvider.GITHUB,
                "12345"
        );
        ReflectionTestUtils.setField(user, "id", 1L);

        OAuth2AuthenticationToken authentication = githubAuthenticationWithPrivateEmail();
        OAuth2AuthorizedClient authorizedClient = githubAuthorizedClient();

        given(authorizedClientServiceProvider.getIfAvailable()).willReturn(authorizedClientService);
        given(authorizedClientService.loadAuthorizedClient("github", authentication.getName()))
                .willReturn(authorizedClient);
        given(userEmailResolver.resolve(any(), any()))
                .willReturn(new ResolvedOAuth2UserInfo(
                        AuthProvider.GITHUB,
                        "12345",
                        "primary@example.com",
                        "octocat"
                ));
        given(oAuth2AuthService.findOrCreateUser(any())).willReturn(user);
        given(authorizationCodeStore.save(1L))
                .willReturn(new OAuth2AuthorizationCode(
                        "oauth2-authorization-code",
                        1L,
                        Instant.parse("2026-06-12T10:05:00Z")
                ));
        given(oAuth2Properties.successRedirectUri())
                .willReturn("http://localhost:3000/oauth2/success");

        successHandler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<OAuth2UserInfo> userInfoCaptor = ArgumentCaptor.forClass(OAuth2UserInfo.class);

        verify(oAuth2AuthService).findOrCreateUser(userInfoCaptor.capture());
        assertThat(userInfoCaptor.getValue().getEmail()).isEqualTo("primary@example.com");
        verify(providerTokenService).saveOrReplace(any());
        verify(response).sendRedirect("http://localhost:3000/oauth2/success?code=oauth2-authorization-code");
    }

    @Test
    @DisplayName("authorized client가 없으면 provider token 저장을 조용히 건너뛰지 않는다")
    void onAuthenticationSuccessWithoutAuthorizedClient() {
        OAuth2AuthenticationToken authentication = githubAuthentication();

        given(authorizedClientServiceProvider.getIfAvailable()).willReturn(authorizedClientService);
        given(authorizedClientService.loadAuthorizedClient("github", authentication.getName()))
                .willReturn(null);

        assertThatThrownBy(() -> successHandler.onAuthenticationSuccess(request, response, authentication))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_INVALID);
    }

    @Test
    @DisplayName("authorized client service가 없으면 provider token 저장을 조용히 건너뛰지 않는다")
    void onAuthenticationSuccessWithoutAuthorizedClientService() {
        given(authorizedClientServiceProvider.getIfAvailable()).willReturn(null);

        assertThatThrownBy(() -> successHandler.onAuthenticationSuccess(request, response, githubAuthentication()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_INVALID);
    }

    private OAuth2AuthenticationToken githubAuthentication() {
        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "id", 12345,
                        "login", "octocat",
                        "name", "octocat",
                        "email", "octocat@example.com"
                ),
                "id"
        );

        return new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "github"
        );
    }

    private OAuth2AuthenticationToken githubAuthenticationWithPrivateEmail() {
        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "id", 12345,
                        "login", "octocat",
                        "name", "octocat"
                ),
                "id"
        );

        return new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "github"
        );
    }

    private OAuth2AuthorizedClient githubAuthorizedClient() {
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "github-provider-access-token",
                Instant.parse("2026-06-12T10:00:00Z"),
                Instant.parse("2026-06-12T11:00:00Z"),
                Set.of("repo", "read:user")
        );

        return new OAuth2AuthorizedClient(
                githubClientRegistration(),
                "12345",
                accessToken
        );
    }

    private ClientRegistration githubClientRegistration() {
        return ClientRegistration.withRegistrationId("github")
                .clientId("github-client-id")
                .clientSecret("github-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read:user", "repo")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("id")
                .clientName("GitHub")
                .build();
    }
}
