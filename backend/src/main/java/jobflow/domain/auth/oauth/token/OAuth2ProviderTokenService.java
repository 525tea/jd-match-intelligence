package jobflow.domain.auth.oauth.token;

import java.util.Optional;
import jobflow.domain.user.AuthProvider;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OAuth2ProviderTokenService {

    private final UserRepository userRepository;
    private final UserOAuthTokenRepository userOAuthTokenRepository;
    private final OAuth2ProviderTokenEncryptor tokenEncryptor;

    @Transactional
    public void saveOrReplace(OAuth2ProviderTokenCommand command) {
        validate(command);

        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String encryptedAccessToken = tokenEncryptor.encrypt(command.accessToken());

        userOAuthTokenRepository.findByUserIdAndAuthProvider(user.getId(), command.authProvider())
                .ifPresentOrElse(
                        token -> token.replace(
                                encryptedAccessToken,
                                command.tokenType(),
                                command.normalizedScopes(),
                                command.issuedAt(),
                                command.expiresAt()
                        ),
                        () -> userOAuthTokenRepository.save(UserOAuthToken.create(
                                user,
                                command.authProvider(),
                                encryptedAccessToken,
                                command.tokenType(),
                                command.normalizedScopes(),
                                command.issuedAt(),
                                command.expiresAt()
                        ))
                );
    }

    public Optional<String> findAccessToken(Long userId, AuthProvider authProvider) {
        if (userId == null || authProvider == null || authProvider == AuthProvider.LOCAL) {
            return Optional.empty();
        }

        return userOAuthTokenRepository.findByUserIdAndAuthProvider(userId, authProvider)
                .map(UserOAuthToken::getEncryptedAccessToken)
                .map(tokenEncryptor::decrypt);
    }

    public String getRequiredAccessToken(Long userId, AuthProvider authProvider) {
        return findAccessToken(userId, authProvider)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_NOT_FOUND));
    }

    @Transactional
    public void delete(Long userId, AuthProvider authProvider) {
        if (userId == null || authProvider == null || authProvider == AuthProvider.LOCAL) {
            return;
        }

        userOAuthTokenRepository.deleteByUserIdAndAuthProvider(userId, authProvider);
    }

    private void validate(OAuth2ProviderTokenCommand command) {
        if (command == null || command.userId() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (command.authProvider() == null || command.authProvider() == AuthProvider.LOCAL) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_PROVIDER_NOT_SUPPORTED);
        }

        if (!StringUtils.hasText(command.accessToken())) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_INVALID);
        }
    }
}
