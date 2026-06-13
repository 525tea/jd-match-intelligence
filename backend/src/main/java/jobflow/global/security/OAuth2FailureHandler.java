package jobflow.global.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import jobflow.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Slf4j
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private final OAuth2Properties oAuth2Properties;

    public OAuth2FailureHandler(OAuth2Properties oAuth2Properties) {
        this.oAuth2Properties = oAuth2Properties;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        logAuthenticationFailure(exception);

        String redirectUri = UriComponentsBuilder
                .fromUriString(oAuth2Properties.failureRedirectUri())
                .queryParam("error", ErrorCode.COMMON_UNAUTHORIZED.getCode())
                .build()
                .toUriString();

        response.sendRedirect(redirectUri);
    }

    private void logAuthenticationFailure(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            log.warn(
                    "OAuth2 authentication failed. errorCode={}, description={}, exceptionType={}",
                    oauth2Exception.getError().getErrorCode(),
                    oauth2Exception.getError().getDescription(),
                    exception.getClass().getSimpleName()
            );
            return;
        }

        log.warn(
                "OAuth2 authentication failed. message={}, exceptionType={}",
                exception.getMessage(),
                exception.getClass().getSimpleName()
        );
    }
}
