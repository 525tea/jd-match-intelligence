package jobflow.global.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    private static final int COOKIE_EXPIRE_SECONDS = 180;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return resolveCookie(request)
                .map(Cookie::getValue)
                .flatMap(this::deserialize)
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authorizationRequest == null) {
            clearCookie(response);
            return;
        }

        String value = serialize(authorizationRequest);
        ResponseCookie cookie = baseCookie(value)
                .maxAge(Duration.ofSeconds(COOKIE_EXPIRE_SECONDS))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        clearCookie(response);
        return authorizationRequest;
    }

    private Optional<Cookie> resolveCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME.equals(cookie.getName()))
                .findFirst();
    }

    private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        try (
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(bytes)
        ) {
            objectOutputStream.writeObject(authorizationRequest);
            return Base64.getUrlEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("OAuth2 authorization request serialization failed.", exception);
        }
    }

    private Optional<OAuth2AuthorizationRequest> deserialize(String value) {
        try (
                ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getUrlDecoder().decode(value));
                ObjectInputStream objectInputStream = new ObjectInputStream(bytes)
        ) {
            return Optional.of((OAuth2AuthorizationRequest) objectInputStream.readObject());
        } catch (IOException | ClassNotFoundException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private void clearCookie(HttpServletResponse response) {
        ResponseCookie cookie = baseCookie("")
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/");
    }
}
