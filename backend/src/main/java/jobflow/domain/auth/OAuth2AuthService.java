package jobflow.domain.auth;

import jobflow.domain.auth.oauth.OAuth2UserInfo;
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
public class OAuth2AuthService {

    private final UserRepository userRepository;

    @Transactional
    public User findOrCreateUser(OAuth2UserInfo userInfo) {
        validateUserInfo(userInfo);

        AuthProvider provider = userInfo.getProvider();
        String providerId = userInfo.getProviderId();

        return userRepository.findByAuthProviderAndProviderId(provider, providerId)
                .orElseGet(() -> createOAuth2User(userInfo));
    }

    private User createOAuth2User(OAuth2UserInfo userInfo) {
        if (userRepository.existsByEmail(userInfo.getEmail())) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_USED);
        }

        User user = User.oauth2(
                userInfo.getEmail(),
                userInfo.getName(),
                userInfo.getProvider(),
                userInfo.getProviderId()
        );

        return userRepository.save(user);
    }

    private void validateUserInfo(OAuth2UserInfo userInfo) {
        if (!StringUtils.hasText(userInfo.getProviderId())) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_PROVIDER_NOT_SUPPORTED);
        }

        if (!StringUtils.hasText(userInfo.getEmail())) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_EMAIL_NOT_FOUND);
        }
    }
}
