package jobflow.domain.project.analysis;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class UnavailableRepositoryFileClient implements RepositoryFileClient {

    private static final String MESSAGE =
            "Repository file client requires GitHub provider access token integration";

    @Override
    public Optional<RepositoryFile> findFile(
            RepositoryRef repositoryRef,
            String path
    ) {
        throw new IllegalStateException(MESSAGE);
    }
}
