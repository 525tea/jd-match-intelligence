package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jobflow.domain.project.UserProject;
import jobflow.domain.project.UserProjectAnalysis;
import jobflow.domain.project.UserProjectAnalysisRepository;
import jobflow.domain.project.UserProjectRepository;
import jobflow.domain.project.UserProjectSkill;
import jobflow.domain.project.UserProjectSkillRepository;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillCategory;
import jobflow.domain.skill.SkillRepository;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.ArgumentCaptor;

class ProjectBuildFileSkillImportServiceTest {

    private final UserProjectRepository userProjectRepository =
            mock(UserProjectRepository.class);

    private final UserProjectAnalysisRepository userProjectAnalysisRepository =
            mock(UserProjectAnalysisRepository.class);

    private final UserProjectSkillRepository userProjectSkillRepository =
            mock(UserProjectSkillRepository.class);

    private final SkillRepository skillRepository =
            mock(SkillRepository.class);

    private final ProjectBuildFileAnalysisService projectBuildFileAnalysisService =
            mock(ProjectBuildFileAnalysisService.class);

    private final ProjectBuildFileSkillImportService service =
            new ProjectBuildFileSkillImportService(
                    userProjectRepository,
                    userProjectAnalysisRepository,
                    userProjectSkillRepository,
                    skillRepository,
                    projectBuildFileAnalysisService
            );

    @Test
    @DisplayName("빌드 파일 분석 skill 후보를 기존 skill과 매칭해 프로젝트 분석 결과로 저장한다")
    void importBuildFileSkills() {
        Long userId = 1L;
        Long userProjectId = 10L;
        RepositoryRef repositoryRef = new RepositoryRef("525tea", "jobflow", "main");
        UserProject userProject = mock(UserProject.class);
        Skill java = Skill.create("Java", "java", SkillCategory.LANGUAGE);
        Skill springBoot = Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK);
        List<UserProjectSkill> savedProjectSkills = new ArrayList<>();
        ProjectBuildFileAnalysisResult analysisResult = new ProjectBuildFileAnalysisResult(
                repositoryRef,
                3,
                2,
                List.of("backend/build.gradle", "frontend/package.json"),
                List.of(
                        BuildFileSkillCandidate.of(
                                "Java",
                                "plugin:java",
                                0.90
                        ),
                        BuildFileSkillCandidate.of(
                                "Spring Boot",
                                "gradle dependency org.springframework.boot:spring-boot-starter-web",
                                0.95
                        ),
                        BuildFileSkillCandidate.of(
                                "Next.js",
                                "package.json dependency next",
                                0.95
                        )
                )
        );
        given(userProjectRepository.findByIdAndUserId(userProjectId, userId))
                .willReturn(Optional.of(userProject));
        given(projectBuildFileAnalysisService.analyze(repositoryRef))
                .willReturn(analysisResult);
        given(skillRepository.findByNameIn(ArgumentMatchers.<Collection<String>>any()))
                .willReturn(List.of(java, springBoot));
        given(userProjectAnalysisRepository.findMaxAnalysisVersionByUserProjectId(userProjectId))
                .willReturn(2);
        given(userProjectAnalysisRepository.save(any(UserProjectAnalysis.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(userProjectSkillRepository.saveAll(ArgumentMatchers.<Iterable<UserProjectSkill>>any()))
                .willAnswer(invocation -> {
                    Iterable<UserProjectSkill> projectSkills = invocation.getArgument(0);
                    projectSkills.forEach(savedProjectSkills::add);
                    return savedProjectSkills;
                });

        ProjectBuildFileSkillImportResult result = service.importBuildFileSkills(
                userId,
                userProjectId,
                repositoryRef
        );

        assertThat(result.analysisVersion()).isEqualTo(3);
        assertThat(result.candidateSkillCount()).isEqualTo(3);
        assertThat(result.savedSkillCount()).isEqualTo(2);
        assertThat(result.savedSkillNames()).containsExactly("Java", "Spring Boot");
        assertThat(result.unmappedSkillNames()).containsExactly("Next.js");

        ArgumentCaptor<UserProjectAnalysis> analysisCaptor =
                ArgumentCaptor.forClass(UserProjectAnalysis.class);
        verify(userProjectAnalysisRepository).save(analysisCaptor.capture());
        assertThat(analysisCaptor.getValue().getAnalysisVersion()).isEqualTo(3);
        assertThat(analysisCaptor.getValue().getSourceHash()).hasSize(64);
        assertThat(analysisCaptor.getValue().getCommitSha()).isEqualTo("main");
        assertThat(analysisCaptor.getValue().getModelVersion()).isEqualTo("build-file-static-v1");
        assertThat(analysisCaptor.getValue().getRawAnalysis()).contains("\"repository\":\"525tea/jobflow\"");

        verify(userProjectSkillRepository).saveAll(ArgumentMatchers.<Iterable<UserProjectSkill>>any());
        assertThat(savedProjectSkills)
                .extracting(projectSkill -> projectSkill.getSkill().getName())
                .containsExactly("Java", "Spring Boot");
    }

    @Test
    @DisplayName("프로젝트가 없거나 소유자가 다르면 USER_PROJECT_NOT_FOUND 예외를 던진다")
    void importBuildFileSkillsWithoutOwnedProject() {
        Long userId = 1L;
        Long userProjectId = 10L;
        RepositoryRef repositoryRef = RepositoryRef.of("525tea", "jobflow");
        given(userProjectRepository.findByIdAndUserId(userProjectId, userId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.importBuildFileSkills(userId, userProjectId, repositoryRef))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_PROJECT_NOT_FOUND);

        verifyNoInteractions(
                projectBuildFileAnalysisService,
                userProjectAnalysisRepository,
                userProjectSkillRepository,
                skillRepository
        );
    }

    @Test
    @DisplayName("사용자나 프로젝트 식별자가 없으면 USER_PROJECT_NOT_FOUND 예외를 던진다")
    void importBuildFileSkillsWithoutIdentifiers() {
        RepositoryRef repositoryRef = RepositoryRef.of("525tea", "jobflow");

        assertThatThrownBy(() -> service.importBuildFileSkills(null, 10L, repositoryRef))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_PROJECT_NOT_FOUND);

        assertThatThrownBy(() -> service.importBuildFileSkills(1L, null, repositoryRef))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_PROJECT_NOT_FOUND);

        verifyNoInteractions(
                userProjectRepository,
                projectBuildFileAnalysisService,
                userProjectAnalysisRepository,
                userProjectSkillRepository,
                skillRepository
        );
    }
}
