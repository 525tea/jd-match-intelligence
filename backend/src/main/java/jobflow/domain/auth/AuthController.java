package jobflow.domain.auth;

import jakarta.validation.Valid;
import jobflow.domain.auth.dto.*;
import jobflow.global.response.ApiResponse;
import jobflow.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jobflow.domain.auth.dto.OAuth2TokenRequest;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/oauth2/token")
    public ResponseEntity<ApiResponse<LoginResponse>> exchangeOAuth2Code(
            @Valid @RequestBody OAuth2TokenRequest request
    ) {
        LoginResponse response = authService.exchangeOAuth2Code(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        MeResponse response = new MeResponse(
                principal.id(),
                principal.email(),
                principal.name(),
                principal.role()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
