package jobflow.global.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import jobflow.global.error.ErrorCode;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
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
        String redirectUri = UriComponentsBuilder
                .fromUriString(oAuth2Properties.failureRedirectUri())
                .queryParam("error", ErrorCode.COMMON_UNAUTHORIZED.getCode())
                .build()
                .toUriString();

        response.sendRedirect(redirectUri);
    }
}
