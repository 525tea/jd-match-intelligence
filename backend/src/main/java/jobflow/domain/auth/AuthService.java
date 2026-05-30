package jobflow.domain.auth;

import jobflow.domain.auth.dto.LoginRequest;
import jobflow.domain.auth.dto.LoginResponse;
import jobflow.domain.auth.dto.SignupRequest;
import jobflow.domain.auth.dto.SignupResponse;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import jobflow.global.error.exception.ConflictException;
import jobflow.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jobflow.domain.auth.dto.OAuth2TokenRequest;
import jobflow.domain.auth.oauth.code.OAuth2AuthorizationCode;
import jobflow.domain.auth.oauth.code.OAuth2AuthorizationCodeStore;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final OAuth2AuthorizationCodeStore authorizationCodeStore;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException(ErrorCode.USER_EMAIL_DUPLICATED);
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = User.signup(request.email(), passwordHash, request.name());
        User savedUser = userRepository.save(user);

        return new SignupResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getName()
        );
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user);

        return LoginResponse.bearer(
                accessToken,
                jwtTokenProvider.getAccessTokenExpirationMillis()
        );
    }

    public LoginResponse exchangeOAuth2Code(OAuth2TokenRequest request) {
        OAuth2AuthorizationCode authorizationCode = authorizationCodeStore.consume(request.code());

        User user = userRepository.findById(authorizationCode.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_OAUTH2_CODE_INVALID));

        String accessToken = jwtTokenProvider.createAccessToken(user);

        return LoginResponse.bearer(
                accessToken,
                jwtTokenProvider.getAccessTokenExpirationMillis()
        );
    }
}
