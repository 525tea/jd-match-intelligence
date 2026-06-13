package jobflow.domain.project.analysis;

import java.util.List;
import java.util.Optional;

public interface RepositoryFileClient {

    Optional<RepositoryFile> findFile(RepositoryRef repositoryRef, String path);

    default Optional<RepositoryFile> findFile(Long userId, RepositoryRef repositoryRef, String path) {
        return findFile(repositoryRef, path);
    }

    default List<RepositoryFile> findFiles(
            RepositoryRef repositoryRef,
            List<String> paths
    ) {
        return findFiles(null, repositoryRef, paths);
    }

    default List<RepositoryFile> findFiles(
            Long userId,
            RepositoryRef repositoryRef,
            List<String> paths
    ) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }

        return paths.stream()
                .map(path -> findFile(userId, repositoryRef, path))
                .flatMap(Optional::stream)
                .toList();
    }
}
