package jobflow.domain.project.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import jobflow.domain.project.ProjectSourceType;
import jobflow.domain.project.UserProject;
import jobflow.domain.project.UserProjectRepository;
import jobflow.domain.project.analysis.ProjectRepositoryStaticAnalysisImportResult;
import jobflow.domain.project.analysis.ProjectRepositoryStaticAnalysisImportService;
import jobflow.domain.project.analysis.RepositoryRef;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GitHubRepositoryAnalysisServiceTest {

    private final GitHubRepositoryCatalogClient repositoryCatalogClient =
            mock(GitHubRepositoryCatalogClient.class);

    private final ProjectRepositoryStaticAnalysisImportService staticAnalysisImportService =
            mock(ProjectRepositoryStaticAnalysisImportService.class);

    private final UserRepository userRepository =
            mock(UserRepository.class);

    private final UserProjectRepository userProjectRepository =
            mock(UserProjectRepository.class);

    private final GitHubRepositoryAnalysisService service =
            new GitHubRepositoryAnalysisService(
                    repositoryCatalogClient,
                    staticAnalysisImportService,
                    userRepository,
                    userProjectRepository
            );

    @Test
    @DisplayName("GitHub repository를 사용자 프로젝트로 등록하고 static analysis import를 실행한다")
    void importRepository() {
        Long userId = 1L;
        User user = User.oauth2(
                "user@example.com",
                "테스트 사용자",
                jobflow.domain.user.AuthProvider.GITHUB,
                "test-provider-user"
        );
        UserProject savedProject = mock(UserProject.class);
        ProjectRepositoryStaticAnalysisImportResult importResult =
                new ProjectRepositoryStaticAnalysisImportResult(
                        100L,
                        2,
                        false,
                        3,
                        2,
                        List.of("Java", "Spring Boot"),
                        List.of("Next.js"),
                        2,
                        1,
                        List.of("CI_CD"),
                        List.of("UNKNOWN_TAG")
                );
        GitHubRepositoryImportRequest request = new GitHubRepositoryImportRequest(
                "example-org",
                "sample-repo",
                "main",
                "https://github.example/example-org/sample-repo",
                "sample repository"
        );
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userProjectRepository.findByUserIdAndSourceTypeAndExternalId(
                userId,
                ProjectSourceType.GITHUB,
                "example-org/sample-repo"
        )).willReturn(Optional.empty());
        given(userProjectRepository.save(any(UserProject.class))).willReturn(savedProject);
        given(savedProject.getId()).willReturn(10L);
        given(staticAnalysisImportService.importRepositoryStaticAnalysis(
                userId,
                10L,
                new RepositoryRef("example-org", "sample-repo", "main")
        )).willReturn(importResult);

        GitHubRepositoryImportResponse response = service.importRepository(userId, request);

        assertThat(response.userProjectId()).isEqualTo(10L);
        assertThat(response.repositoryFullName()).isEqualTo("example-org/sample-repo");
        assertThat(response.ref()).isEqualTo("main");
        assertThat(response.analysisId()).isEqualTo(100L);
        assertThat(response.savedSkillNames()).containsExactly("Java", "Spring Boot");
        assertThat(response.savedTagCodes()).containsExactly("CI_CD");
        verify(userProjectRepository).save(any(UserProject.class));
        verify(staticAnalysisImportService).importRepositoryStaticAnalysis(
                userId,
                10L,
                new RepositoryRef("example-org", "sample-repo", "main")
        );
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 USER_NOT_FOUND를 반환한다")
    void importRepositoryWithMissingUser() {
        Long userId = 1L;
        GitHubRepositoryImportRequest request = new GitHubRepositoryImportRequest(
                "example-org",
                "sample-repo",
                "main",
                null,
                null
        );
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.importRepository(userId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
