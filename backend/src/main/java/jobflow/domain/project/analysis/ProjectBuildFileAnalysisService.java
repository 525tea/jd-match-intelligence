package jobflow.domain.project.analysis;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProjectBuildFileAnalysisService {

    private static final List<String> DEFAULT_BUILD_FILE_PATHS = List.of(
            "build.gradle",
            "build.gradle.kts",
            "pom.xml",
            "package.json",
            "backend/build.gradle",
            "backend/build.gradle.kts",
            "backend/pom.xml",
            "frontend/package.json",
            "app/build.gradle",
            "app/build.gradle.kts",
            "server/build.gradle",
            "server/build.gradle.kts",
            "server/pom.xml",
            "client/package.json"
    );

    private final RepositoryFileClient repositoryFileClient;
    private final BuildFileSkillAnalyzer buildFileSkillAnalyzer;

    public ProjectBuildFileAnalysisService(RepositoryFileClient repositoryFileClient) {
        this(repositoryFileClient, BuildFileSkillAnalyzer.defaultAnalyzer());
    }

    ProjectBuildFileAnalysisService(
            RepositoryFileClient repositoryFileClient,
            BuildFileSkillAnalyzer buildFileSkillAnalyzer
    ) {
        this.repositoryFileClient = repositoryFileClient;
        this.buildFileSkillAnalyzer = buildFileSkillAnalyzer;
    }

    public ProjectBuildFileAnalysisResult analyze(RepositoryRef repositoryRef) {
        return analyze(repositoryRef, DEFAULT_BUILD_FILE_PATHS);
    }

    public ProjectBuildFileAnalysisResult analyze(
            RepositoryRef repositoryRef,
            List<String> candidatePaths
    ) {
        if (repositoryRef == null) {
            throw new IllegalArgumentException("repositoryRef must not be null");
        }
        if (candidatePaths == null || candidatePaths.isEmpty()) {
            return ProjectBuildFileAnalysisResult.empty(repositoryRef, 0);
        }

        List<RepositoryFile> repositoryFiles = repositoryFileClient.findFiles(repositoryRef, candidatePaths);
        List<RepositoryBuildFile> buildFiles = repositoryFiles.stream()
                .map(RepositoryFile::toBuildFile)
                .filter(buildFile -> buildFile.type() != BuildFileType.UNKNOWN)
                .toList();

        if (buildFiles.isEmpty()) {
            return ProjectBuildFileAnalysisResult.empty(repositoryRef, candidatePaths.size());
        }

        List<BuildFileSkillCandidate> skillCandidates = buildFileSkillAnalyzer.analyze(buildFiles);
        List<String> analyzedPaths = buildFiles.stream()
                .map(RepositoryBuildFile::path)
                .toList();

        return new ProjectBuildFileAnalysisResult(
                repositoryRef,
                candidatePaths.size(),
                buildFiles.size(),
                analyzedPaths,
                skillCandidates
        );
    }
}
