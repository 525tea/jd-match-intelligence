package jobflow.domain.project.analysis;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProjectWorkflowFileAnalysisService {

    private static final List<String> DEFAULT_WORKFLOW_FILE_PATHS = List.of(
            ".github/workflows/backend-ci.yml",
            ".github/workflows/backend-ci.yaml",
            ".github/workflows/collector-ci.yml",
            ".github/workflows/collector-ci.yaml",
            ".github/workflows/docker-image.yml",
            ".github/workflows/docker-image.yaml",
            ".github/workflows/ci.yml",
            ".github/workflows/ci.yaml",
            ".github/workflows/test.yml",
            ".github/workflows/test.yaml",
            ".github/workflows/build.yml",
            ".github/workflows/build.yaml",
            ".github/workflows/deploy.yml",
            ".github/workflows/deploy.yaml",
            ".github/workflows/release.yml",
            ".github/workflows/release.yaml",
            ".github/workflows/codeql.yml",
            ".github/workflows/codeql.yaml",
            ".github/workflows/security.yml",
            ".github/workflows/security.yaml"
    );

    private final RepositoryFileClient repositoryFileClient;
    private final GitHubActionsWorkflowExperienceTagParser parser;

    @Autowired
    public ProjectWorkflowFileAnalysisService(RepositoryFileClient repositoryFileClient) {
        this(repositoryFileClient, new GitHubActionsWorkflowExperienceTagParser());
    }

    ProjectWorkflowFileAnalysisService(
            RepositoryFileClient repositoryFileClient,
            GitHubActionsWorkflowExperienceTagParser parser
    ) {
        this.repositoryFileClient = repositoryFileClient;
        this.parser = parser;
    }

    public ProjectWorkflowFileAnalysisResult analyze(RepositoryRef repositoryRef) {
        return analyze(repositoryRef, DEFAULT_WORKFLOW_FILE_PATHS);
    }

    public ProjectWorkflowFileAnalysisResult analyze(
            RepositoryRef repositoryRef,
            List<String> candidatePaths
    ) {
        if (repositoryRef == null) {
            throw new IllegalArgumentException("repositoryRef must not be null");
        }
        if (candidatePaths == null || candidatePaths.isEmpty()) {
            return ProjectWorkflowFileAnalysisResult.empty(repositoryRef, 0);
        }

        List<RepositoryWorkflowFile> workflowFiles = repositoryFileClient.findFiles(repositoryRef, candidatePaths)
                .stream()
                .map(repositoryFile -> RepositoryWorkflowFile.fromPath(repositoryFile.path(), repositoryFile.content()))
                .filter(RepositoryWorkflowFile::isSupported)
                .toList();

        if (workflowFiles.isEmpty()) {
            return ProjectWorkflowFileAnalysisResult.empty(repositoryRef, candidatePaths.size());
        }

        List<WorkflowExperienceTagCandidate> experienceTagCandidates = deduplicateCandidates(
                workflowFiles.stream()
                        .flatMap(workflowFile -> parser.parse(workflowFile).stream())
                        .toList()
        );
        List<String> analyzedPaths = workflowFiles.stream()
                .map(RepositoryWorkflowFile::path)
                .toList();

        return new ProjectWorkflowFileAnalysisResult(
                repositoryRef,
                candidatePaths.size(),
                workflowFiles.size(),
                analyzedPaths,
                experienceTagCandidates
        );
    }

    private List<WorkflowExperienceTagCandidate> deduplicateCandidates(
            List<WorkflowExperienceTagCandidate> candidates
    ) {
        Map<String, WorkflowExperienceTagCandidate> candidatesByTagAndEvidence = candidates.stream()
                .collect(Collectors.toMap(
                        candidate -> candidate.tagCode() + "|" + candidate.evidence(),
                        Function.identity(),
                        this::chooseHigherConfidence,
                        LinkedHashMap::new
                ));

        return candidatesByTagAndEvidence.values()
                .stream()
                .sorted(Comparator.comparing(WorkflowExperienceTagCandidate::tagCode)
                        .thenComparing(WorkflowExperienceTagCandidate::evidence))
                .toList();
    }

    private WorkflowExperienceTagCandidate chooseHigherConfidence(
            WorkflowExperienceTagCandidate current,
            WorkflowExperienceTagCandidate candidate
    ) {
        if (candidate.confidence().compareTo(current.confidence()) > 0) {
            return candidate;
        }
        return current;
    }
}
