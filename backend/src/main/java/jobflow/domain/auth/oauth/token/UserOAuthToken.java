package jobflow.domain.auth.oauth.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import jobflow.domain.common.BaseTimeEntity;
import jobflow.domain.user.AuthProvider;
import jobflow.domain.user.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "user_oauth_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserOAuthToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 30)
    private AuthProvider authProvider;

    @Column(name = "encrypted_access_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedAccessToken;

    @Column(name = "token_type", length = 30)
    private String tokenType;

    @Column(length = 500)
    private String scopes;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public static UserOAuthToken create(
            User user,
            AuthProvider authProvider,
            String encryptedAccessToken,
            String tokenType,
            String scopes,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
        UserOAuthToken token = new UserOAuthToken();
        token.user = user;
        token.authProvider = authProvider;
        token.encryptedAccessToken = encryptedAccessToken;
        token.tokenType = tokenType;
        token.scopes = scopes;
        token.issuedAt = issuedAt;
        token.expiresAt = expiresAt;
        return token;
    }

    public void replace(
            String encryptedAccessToken,
            String tokenType,
            String scopes,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
        this.encryptedAccessToken = encryptedAccessToken;
        this.tokenType = tokenType;
        this.scopes = scopes;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }
}
