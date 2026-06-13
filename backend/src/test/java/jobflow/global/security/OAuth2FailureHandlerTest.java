package jobflow.global.security;

import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

@ExtendWith(MockitoExtension.class)
class OAuth2FailureHandlerTest {

    @Mock
    private OAuth2Properties oAuth2Properties;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private OAuth2FailureHandler failureHandler;

    @BeforeEach
    void setUp() {
        failureHandler = new OAuth2FailureHandler(oAuth2Properties);
    }

    @Test
    @DisplayName("OAuth2 인증 실패 시 failure redirect URI로 공통 인증 오류를 전달한다")
    void onAuthenticationFailure() throws Exception {
        org.mockito.BDDMockito.given(oAuth2Properties.failureRedirectUri())
                .willReturn("http://localhost:3000/oauth2/failure");

        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                new OAuth2Error(
                        "invalid_client",
                        "Bad client credentials",
                        "https://docs.github.com/apps/oauth-apps"
                )
        );

        failureHandler.onAuthenticationFailure(request, response, exception);

        verify(response).sendRedirect("http://localhost:3000/oauth2/failure?error=COMMON_UNAUTHORIZED");
    }
}
