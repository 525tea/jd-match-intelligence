package jobflow.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("회원가입 성공 시 비밀번호를 해시하고 사용자를 저장한다")
    void signup() {
        SignupRequest request = new SignupRequest(
                "test@example.com",
                "password123",
                "테스트"
        );

        User savedUser = User.signup(
                request.email(),
                "encoded-password",
                request.name()
        );
        ReflectionTestUtils.setField(savedUser, "id", 1L);

        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willReturn(savedUser);

        SignupResponse response = authService.signup(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.name()).isEqualTo("테스트");

        verify(userRepository).existsByEmail(request.email());
        verify(passwordEncoder).encode(request.password());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("이미 존재하는 이메일로 회원가입하면 ConflictException이 발생한다")
    void signupWithDuplicatedEmail() {
        SignupRequest request = new SignupRequest(
                "test@example.com",
                "password123",
                "테스트"
        );

        given(userRepository.existsByEmail(request.email())).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_EMAIL_DUPLICATED);
    }

    @Test
    @DisplayName("로그인 성공 시 JWT access token을 반환한다")
    void login() {
        LoginRequest request = new LoginRequest(
                "test@example.com",
                "password123"
        );

        User user = User.signup(
                request.email(),
                "encoded-password",
                "테스트"
        );
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), user.getPasswordHash())).willReturn(true);
        given(jwtTokenProvider.createAccessToken(user)).willReturn("access-token");
        given(jwtTokenProvider.getAccessTokenExpirationMillis()).willReturn(3600000L);

        LoginResponse response = authService.login(request);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.expiresIn()).isEqualTo(3600000L);

        verify(userRepository).findByEmail(request.email());
        verify(passwordEncoder).matches(request.password(), user.getPasswordHash());
        verify(jwtTokenProvider).createAccessToken(user);
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인하면 인증 실패 예외가 발생한다")
    void loginWithUnknownEmail() {
        LoginRequest request = new LoginRequest(
                "unknown@example.com",
                "password123"
        );

        given(userRepository.findByEmail(request.email())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 인증 실패 예외가 발생한다")
    void loginWithInvalidPassword() {
        LoginRequest request = new LoginRequest(
                "test@example.com",
                "wrong-password"
        );

        User user = User.signup(
                request.email(),
                "encoded-password",
                "테스트"
        );
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), user.getPasswordHash())).willReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
}
