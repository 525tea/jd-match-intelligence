package jobflow.domain.auth.admin;

import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.domain.user.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(
        prefix = "app.auth.admin-bootstrap",
        name = "enabled",
        havingValue = "true"
)
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AdminBootstrapProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(
            AdminBootstrapProperties properties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        validateProperties();

        String encodedPassword = passwordEncoder.encode(properties.password());

        userRepository.findByEmail(properties.email())
                .ifPresentOrElse(
                        user -> updateExistingUser(user, encodedPassword),
                        () -> createAdminUser(encodedPassword)
                );
    }

    private void validateProperties() {
        if (!StringUtils.hasText(properties.email())) {
            throw new IllegalStateException("Admin bootstrap email must be configured when enabled.");
        }

        if (!StringUtils.hasText(properties.password())) {
            throw new IllegalStateException("Admin bootstrap password must be configured when enabled.");
        }

        if (properties.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("Admin bootstrap password must be at least 8 characters.");
        }
    }

    private void updateExistingUser(User user, String encodedPassword) {
        if (user.getRole() != UserRole.ADMIN) {
            user.promoteToAdmin();
        }

        user.updatePasswordHash(encodedPassword);
    }

    private void createAdminUser(String encodedPassword) {
        String name = StringUtils.hasText(properties.name())
                ? properties.name()
                : "Admin";

        User admin = User.admin(
                properties.email(),
                encodedPassword,
                name
        );

        userRepository.save(admin);
    }
}
