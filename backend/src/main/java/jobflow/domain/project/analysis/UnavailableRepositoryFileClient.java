package jobflow.domain.project.analysis;

import java.util.Optional;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(RepositoryFileClient.class)
public class UnavailableRepositoryFileClient implements RepositoryFileClient {

    @Override
    public Optional<RepositoryFile> findFile(
            RepositoryRef repositoryRef,
            String path
    ) {
        throw new BusinessException(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_NOT_FOUND);
    }
}
