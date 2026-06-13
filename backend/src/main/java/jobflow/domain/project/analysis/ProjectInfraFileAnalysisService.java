package jobflow.domain.project.analysis;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProjectInfraFileAnalysisService {

    private static final List<String> DEFAULT_INFRA_FILE_PATHS = List.of(
            "Dockerfile",
            "Dockerfile.prod",
            "docker-compose.yml",
            "docker-compose.yaml",
            "compose.yml",
            "compose.yaml",
            "application.yml",
            "application.yaml",
            "application.properties",
            "backend/Dockerfile",
            "backend/Dockerfile.prod",
            "backend/docker-compose.yml",
            "backend/docker-compose.yaml",
            "backend/compose.yml",
            "backend/compose.yaml",
            "backend/src/main/resources/application.yml",
            "backend/src/main/resources/application.yaml",
            "backend/src/main/resources/application.properties",
            "backend/src/main/resources/application-local.yml",
            "backend/src/main/resources/application-local.yaml",
            "backend/src/main/resources/application-local.properties",
            "server/Dockerfile",
            "server/docker-compose.yml",
            "server/src/main/resources/application.yml",
            "server/src/main/resources/application.properties"
    );

    private final RepositoryFileClient repositoryFileClient;
    private final InfraFileExperienceTagAnalyzer infraFileExperienceTagAnalyzer;

    @Autowired
    public ProjectInfraFileAnalysisService(RepositoryFileClient repositoryFileClient) {
        this(repositoryFileClient, InfraFileExperienceTagAnalyzer.defaultAnalyzer());
    }

    ProjectInfraFileAnalysisService(
            RepositoryFileClient repositoryFileClient,
            InfraFileExperienceTagAnalyzer infraFileExperienceTagAnalyzer
    ) {
        this.repositoryFileClient = repositoryFileClient;
        this.infraFileExperienceTagAnalyzer = infraFileExperienceTagAnalyzer;
    }

    public ProjectInfraFileAnalysisResult analyze(RepositoryRef repositoryRef) {
        return analyze(null, repositoryRef, DEFAULT_INFRA_FILE_PATHS);
    }

    public ProjectInfraFileAnalysisResult analyze(Long userId, RepositoryRef repositoryRef) {
        return analyze(userId, repositoryRef, DEFAULT_INFRA_FILE_PATHS);
    }

    public ProjectInfraFileAnalysisResult analyze(
            RepositoryRef repositoryRef,
            List<String> candidatePaths
    ) {
        return analyze(null, repositoryRef, candidatePaths);
    }

    public ProjectInfraFileAnalysisResult analyze(
            Long userId,
            RepositoryRef repositoryRef,
            List<String> candidatePaths
    ) {
        if (repositoryRef == null) {
            throw new IllegalArgumentException("repositoryRef must not be null");
        }
        if (candidatePaths == null || candidatePaths.isEmpty()) {
            return ProjectInfraFileAnalysisResult.empty(repositoryRef, 0);
        }

        List<RepositoryFile> repositoryFiles = repositoryFileClient.findFiles(userId, repositoryRef, candidatePaths);
        List<RepositoryInfraFile> infraFiles = repositoryFiles.stream()
                .map(repositoryFile -> RepositoryInfraFile.fromPath(repositoryFile.path(), repositoryFile.content()))
                .filter(infraFile -> infraFile.type() != InfraFileType.UNKNOWN)
                .toList();

        if (infraFiles.isEmpty()) {
            return ProjectInfraFileAnalysisResult.empty(repositoryRef, candidatePaths.size());
        }

        List<InfraExperienceTagCandidate> experienceTagCandidates = infraFileExperienceTagAnalyzer.analyze(infraFiles);
        List<String> analyzedPaths = infraFiles.stream()
                .map(RepositoryInfraFile::path)
                .toList();

        return new ProjectInfraFileAnalysisResult(
                repositoryRef,
                candidatePaths.size(),
                infraFiles.size(),
                analyzedPaths,
                experienceTagCandidates
        );
    }
}
