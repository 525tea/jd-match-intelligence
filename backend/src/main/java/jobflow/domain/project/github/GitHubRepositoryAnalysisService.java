package jobflow.domain.project.github;

import java.util.List;
import jobflow.domain.project.ProjectSourceType;
import jobflow.domain.project.UserProject;
import jobflow.domain.project.UserProjectRepository;
import jobflow.domain.project.analysis.GitHubRepositoryFileClientException;
import jobflow.domain.project.analysis.ProjectRepositoryStaticAnalysisImportResult;
import jobflow.domain.project.analysis.ProjectRepositoryStaticAnalysisImportService;
import jobflow.domain.project.analysis.RepositoryRef;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GitHubRepositoryAnalysisService {

    private final GitHubRepositoryCatalogClient repositoryCatalogClient;
    private final ProjectRepositoryStaticAnalysisImportService staticAnalysisImportService;
    private final UserRepository userRepository;
    private final UserProjectRepository userProjectRepository;

    public List<GitHubRepositoryResponse> listRepositories(Long userId) {
        try {
            return repositoryCatalogClient.listRepositories(userId);
        } catch (GitHubRepositoryFileClientException exception) {
            throw toBusinessException(exception);
        }
    }

    @Transactional
    public GitHubRepositoryImportResponse importRepository(
            Long userId,
            GitHubRepositoryImportRequest request
    ) {
        RepositoryRef repositoryRef = toRepositoryRef(request);
        UserProject userProject = upsertUserProject(userId, repositoryRef, request);

        try {
            ProjectRepositoryStaticAnalysisImportResult result =
                    staticAnalysisImportService.importRepositoryStaticAnalysis(
                            userId,
                            userProject.getId(),
                            repositoryRef
                    );

            return GitHubRepositoryImportResponse.of(
                    userProject.getId(),
                    repositoryRef.fullName(),
                    repositoryRef.ref(),
                    result
            );
        } catch (GitHubRepositoryFileClientException exception) {
            throw toBusinessException(exception);
        }
    }

    private RepositoryRef toRepositoryRef(GitHubRepositoryImportRequest request) {
        return new RepositoryRef(
                request.owner().trim(),
                request.name().trim(),
                StringUtils.hasText(request.ref()) ? request.ref().trim() : "HEAD"
        );
    }

    private UserProject upsertUserProject(
            Long userId,
            RepositoryRef repositoryRef,
            GitHubRepositoryImportRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String externalId = repositoryRef.fullName();
        String repositoryUrl = StringUtils.hasText(request.htmlUrl())
                ? request.htmlUrl().trim()
                : "https://github.com/" + externalId;
        String description = StringUtils.hasText(request.description())
                ? request.description().trim()
                : null;

        return userProjectRepository
                .findByUserIdAndSourceTypeAndExternalId(userId, ProjectSourceType.GITHUB, externalId)
                .map(project -> {
                    project.updateGithubRepository(repositoryRef.name(), repositoryUrl, description);
                    return project;
                })
                .orElseGet(() -> userProjectRepository.save(UserProject.github(
                        user,
                        externalId,
                        repositoryRef.name(),
                        repositoryUrl,
                        description
                )));
    }

    private BusinessException toBusinessException(GitHubRepositoryFileClientException exception) {
        if (exception.rateLimit().isExhausted()) {
            return new BusinessException(
                    ErrorCode.PROJECT_REPOSITORY_RATE_LIMITED,
                    "GitHub API rate limit이 초과되었습니다. 잠시 후 다시 시도해주세요."
            );
        }

        HttpStatusCode statusCode = exception.statusCode();
        if (statusCode != null && (statusCode.value() == 401 || statusCode.value() == 403)) {
            return new BusinessException(
                    ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_INVALID,
                    "GitHub 저장소 접근 권한이 유효하지 않습니다. GitHub 로그인을 다시 진행해주세요."
            );
        }

        return new BusinessException(
                ErrorCode.PROJECT_REPOSITORY_ACCESS_FAILED,
                "GitHub 저장소 정보를 불러오지 못했습니다."
        );
    }
}
