package jobflow.domain.project.analysis;

import java.util.List;
import java.util.Optional;

public interface RepositoryFileClient {

    Optional<RepositoryFile> findFile(RepositoryRef repositoryRef, String path);

    default List<RepositoryFile> findFiles(
            RepositoryRef repositoryRef,
            List<String> paths
    ) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }

        return paths.stream()
                .map(path -> findFile(repositoryRef, path))
                .flatMap(Optional::stream)
                .toList();
    }
}
