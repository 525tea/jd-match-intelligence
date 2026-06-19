package jobflow.domain.auth;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jobflow.domain.auth.dto.*;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import jobflow.global.response.ApiResponse;
import jobflow.global.security.JwtCookieService;
import jobflow.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtCookieService jwtCookieService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        SignupResponse response = authService.signup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse servletResponse
    ) {
        LoginResponse response = authService.login(request);
        jwtCookieService.addAccessTokenCookie(servletResponse, response.accessToken(), response.expiresIn());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/demo-login")
    public ResponseEntity<ApiResponse<DemoLoginResponse>> demoLogin(
            HttpServletResponse servletResponse
    ) {
        DemoLoginResponse response = authService.demoLogin();
        jwtCookieService.addAccessTokenCookie(servletResponse, response.accessToken(), response.expiresIn());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/oauth2/token")
    public ResponseEntity<ApiResponse<LoginResponse>> exchangeOAuth2Code(
            @Valid @RequestBody OAuth2TokenRequest request,
            HttpServletResponse servletResponse
    ) {
        LoginResponse response = authService.exchangeOAuth2Code(request);
        jwtCookieService.addAccessTokenCookie(servletResponse, response.accessToken(), response.expiresIn());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletResponse servletResponse
    ) {
        jwtCookieService.clearAccessTokenCookie(servletResponse);

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.COMMON_UNAUTHORIZED);
        }

        MeResponse response = new MeResponse(
                principal.id(),
                principal.email(),
                principal.name(),
                principal.role(),
                authService.findLatestProjectId(principal.id())
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
