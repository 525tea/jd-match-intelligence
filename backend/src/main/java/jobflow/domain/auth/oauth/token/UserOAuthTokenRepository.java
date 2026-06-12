package jobflow.domain.auth.oauth.token;

import java.util.Optional;
import jobflow.domain.user.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserOAuthTokenRepository extends JpaRepository<UserOAuthToken, Long> {

    Optional<UserOAuthToken> findByUserIdAndAuthProvider(Long userId, AuthProvider authProvider);

    void deleteByUserIdAndAuthProvider(Long userId, AuthProvider authProvider);
}
