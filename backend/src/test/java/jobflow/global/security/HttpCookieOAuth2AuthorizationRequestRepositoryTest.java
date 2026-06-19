package jobflow.global.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;

class HttpCookieOAuth2AuthorizationRequestRepositoryTest {

    private final HttpCookieOAuth2AuthorizationRequestRepository repository =
            new HttpCookieOAuth2AuthorizationRequestRepository();

    @Test
    @DisplayName("OAuth2 authorization request를 HttpOnly cookie에 저장하고 복원한다")
    void saveAndLoadAuthorizationRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthorizationRequest authorizationRequest = authorizationRequest();

        repository.saveAuthorizationRequest(authorizationRequest, request, response);

        String cookieValue = response.getCookie(
                HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME
        ).getValue();
        request.setCookies(new Cookie(
                HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,
                cookieValue
        ));

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(request);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getState()).isEqualTo("oauth2-state");
        assertThat(loaded.getAuthorizationUri()).isEqualTo("https://github.com/login/oauth/authorize");
        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Lax");
    }

    @Test
    @DisplayName("OAuth2 authorization request 제거 시 cookie를 만료한다")
    void removeAuthorizationRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(authorizationRequest(), request, response);
        String cookieValue = response.getCookie(
                HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME
        ).getValue();
        request.setCookies(new Cookie(
                HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,
                cookieValue
        ));

        OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(request, response);

        assertThat(removed).isNotNull();
        assertThat(response.getHeaders("Set-Cookie")).last().asString().contains("Max-Age=0");
    }

    private OAuth2AuthorizationRequest authorizationRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://github.com/login/oauth/authorize")
                .clientId("github-client-id")
                .redirectUri("http://localhost:8081/api/login/oauth2/code/github")
                .state("oauth2-state")
                .scope("read:user", "user:email")
                .build();
    }
}
