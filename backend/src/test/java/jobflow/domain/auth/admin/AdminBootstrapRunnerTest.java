package jobflow.domain.auth.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.domain.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminBootstrapRunnerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    @DisplayName("ADMIN bootstrap 설정으로 신규 ADMIN 사용자를 생성한다")
    void createAdminUser() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties(
                true,
                "admin@example.com",
                "password123",
                "Admin User"
        );

        given(passwordEncoder.encode("password123")).willReturn("encoded-password");
        given(userRepository.findByEmail("admin@example.com")).willReturn(Optional.empty());

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                properties,
                userRepository,
                passwordEncoder
        );

        runner.run(null);

        verify(userRepository).save(ArgumentMatchers.argThat(user ->
                user.getEmail().equals("admin@example.com")
                        && user.getName().equals("Admin User")
                        && user.getPasswordHash().equals("encoded-password")
                        && user.getRole() == UserRole.ADMIN
        ));
    }

    @Test
    @DisplayName("기존 USER 이메일이 있으면 ADMIN으로 승격하고 비밀번호를 갱신한다")
    void promoteExistingUserToAdmin() {
        User user = User.signup(
                "admin@example.com",
                "old-password",
                "Existing User"
        );

        AdminBootstrapProperties properties = new AdminBootstrapProperties(
                true,
                "admin@example.com",
                "password123",
                "Admin User"
        );

        given(passwordEncoder.encode("password123")).willReturn("new-encoded-password");
        given(userRepository.findByEmail("admin@example.com")).willReturn(Optional.of(user));

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                properties,
                userRepository,
                passwordEncoder
        );

        runner.run(null);

        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(user.getPasswordHash()).isEqualTo("new-encoded-password");
    }

    @Test
    @DisplayName("ADMIN bootstrap이 켜졌는데 이메일이 없으면 시작 실패 예외를 던진다")
    void failWhenEmailMissing() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties(
                true,
                "",
                "password123",
                "Admin User"
        );

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                properties,
                userRepository,
                passwordEncoder
        );

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("ADMIN bootstrap이 켜졌는데 비밀번호가 짧으면 시작 실패 예외를 던진다")
    void failWhenPasswordTooShort() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties(
                true,
                "admin@example.com",
                "short",
                "Admin User"
        );

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                properties,
                userRepository,
                passwordEncoder
        );

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 8 characters");
    }
}
