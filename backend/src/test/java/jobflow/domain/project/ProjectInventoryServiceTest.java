package jobflow.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jobflow.domain.project.dto.ProjectExperienceTagInventoryResponse;
import jobflow.domain.project.dto.ProjectSkillInventoryResponse;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillCategory;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectInventoryServiceTest {

    private final UserProjectRepository userProjectRepository = mock(UserProjectRepository.class);
    private final UserProjectAnalysisRepository userProjectAnalysisRepository =
            mock(UserProjectAnalysisRepository.class);
    private final UserProjectSkillRepository userProjectSkillRepository = mock(UserProjectSkillRepository.class);
    private final UserProjectExperienceTagRepository userProjectExperienceTagRepository =
            mock(UserProjectExperienceTagRepository.class);

    private final ProjectInventoryService projectInventoryService = new ProjectInventoryService(
            userProjectRepository,
            userProjectAnalysisRepository,
            userProjectSkillRepository,
            userProjectExperienceTagRepository
    );

    @Test
    @DisplayName("최신 프로젝트 분석의 스킬 인벤토리를 조회한다")
    void getProjectSkills() {
        UserProjectAnalysis analysis = analysis(10L, 100L, 2);
        UserProjectSkill javaSkill = projectSkill(
                1L,
                "Java",
                "java",
                SkillCategory.LANGUAGE,
                AnalysisSource.STATIC,
                "0.9500",
                "build.gradle implementation"
        );
        UserProjectSkill springSkill = projectSkill(
                2L,
                "Spring Boot",
                "spring boot",
                SkillCategory.FRAMEWORK,
                AnalysisSource.STATIC,
                "0.9000",
                "build.gradle plugin"
        );

        given(userProjectRepository.existsByIdAndUserId(10L, 1L)).willReturn(true);
        given(userProjectAnalysisRepository.findFirstByUserProjectIdAndUserProjectUserIdOrderByAnalyzedAtDescIdDesc(
                10L,
                1L
        )).willReturn(Optional.of(analysis));
        given(userProjectSkillRepository.findByAnalysisIdWithSkill(100L)).willReturn(List.of(javaSkill, springSkill));

        List<ProjectSkillInventoryResponse> responses = projectInventoryService.getProjectSkills(1L, 10L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).userProjectId()).isEqualTo(10L);
        assertThat(responses.get(0).analysisId()).isEqualTo(100L);
        assertThat(responses.get(0).analysisVersion()).isEqualTo(2);
        assertThat(responses.get(0).latestAnalysis()).isTrue();
        assertThat(responses.get(0).skillId()).isEqualTo(1L);
        assertThat(responses.get(0).skillName()).isEqualTo("Java");
        assertThat(responses.get(0).normalizedName()).isEqualTo("java");
        assertThat(responses.get(0).category()).isEqualTo("LANGUAGE");
        assertThat(responses.get(0).source()).isEqualTo(AnalysisSource.STATIC);
        assertThat(responses.get(0).confidence()).isEqualByComparingTo("0.9500");
        assertThat(responses.get(0).evidence()).isEqualTo("build.gradle implementation");

        assertThat(responses.get(1).skillName()).isEqualTo("Spring Boot");
        assertThat(responses.get(1).category()).isEqualTo("FRAMEWORK");
    }

    @Test
    @DisplayName("프로젝트는 존재하지만 분석 결과가 없으면 빈 스킬 목록을 반환한다")
    void getProjectSkillsWithoutAnalysis() {
        given(userProjectRepository.existsByIdAndUserId(10L, 1L)).willReturn(true);
        given(userProjectAnalysisRepository.findFirstByUserProjectIdAndUserProjectUserIdOrderByAnalyzedAtDescIdDesc(
                10L,
                1L
        )).willReturn(Optional.empty());

        List<ProjectSkillInventoryResponse> responses = projectInventoryService.getProjectSkills(1L, 10L);

        assertThat(responses).isEmpty();
        verifyNoInteractions(userProjectSkillRepository);
    }

    @Test
    @DisplayName("최신 프로젝트 분석의 경험 태그 인벤토리를 조회한다")
    void getProjectExperienceTags() {
        UserProjectAnalysis analysis = analysis(10L, 100L, 2);
        UserProjectExperienceTag backendTag = experienceTag(
                "BACKEND_API",
                "백엔드 API",
                "REST API, 서버 개발 경험",
                "0.8800",
                "controller/service package"
        );

        given(userProjectRepository.existsByIdAndUserId(10L, 1L)).willReturn(true);
        given(userProjectAnalysisRepository.findFirstByUserProjectIdAndUserProjectUserIdOrderByAnalyzedAtDescIdDesc(
                10L,
                1L
        )).willReturn(Optional.of(analysis));
        given(userProjectExperienceTagRepository.findByAnalysisIdWithTagCode(100L)).willReturn(List.of(backendTag));

        List<ProjectExperienceTagInventoryResponse> responses =
                projectInventoryService.getProjectExperienceTags(1L, 10L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).userProjectId()).isEqualTo(10L);
        assertThat(responses.get(0).analysisId()).isEqualTo(100L);
        assertThat(responses.get(0).analysisVersion()).isEqualTo(2);
        assertThat(responses.get(0).latestAnalysis()).isTrue();
        assertThat(responses.get(0).tagCode()).isEqualTo("BACKEND_API");
        assertThat(responses.get(0).tagName()).isEqualTo("백엔드 API");
        assertThat(responses.get(0).description()).isEqualTo("REST API, 서버 개발 경험");
        assertThat(responses.get(0).source()).isEqualTo(AnalysisSource.STATIC);
        assertThat(responses.get(0).confidence()).isEqualByComparingTo("0.8800");
        assertThat(responses.get(0).evidence()).isEqualTo("controller/service package");
    }

    @Test
    @DisplayName("프로젝트는 존재하지만 분석 결과가 없으면 빈 경험 태그 목록을 반환한다")
    void getProjectExperienceTagsWithoutAnalysis() {
        given(userProjectRepository.existsByIdAndUserId(10L, 1L)).willReturn(true);
        given(userProjectAnalysisRepository.findFirstByUserProjectIdAndUserProjectUserIdOrderByAnalyzedAtDescIdDesc(
                10L,
                1L
        )).willReturn(Optional.empty());

        List<ProjectExperienceTagInventoryResponse> responses =
                projectInventoryService.getProjectExperienceTags(1L, 10L);

        assertThat(responses).isEmpty();
        verifyNoInteractions(userProjectExperienceTagRepository);
    }

    @Test
    @DisplayName("사용자 소유 프로젝트가 아니면 USER_PROJECT_NOT_FOUND 예외를 던진다")
    void getProjectSkillsWithMissingOwnedProject() {
        given(userProjectRepository.existsByIdAndUserId(999L, 1L)).willReturn(false);

        assertThatThrownBy(() -> projectInventoryService.getProjectSkills(1L, 999L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_PROJECT_NOT_FOUND));

        verifyNoInteractions(
                userProjectAnalysisRepository,
                userProjectSkillRepository,
                userProjectExperienceTagRepository
        );
    }

    @Test
    @DisplayName("사용자 또는 프로젝트 식별자가 없으면 COMMON_INVALID_INPUT 예외를 던진다")
    void getProjectSkillsWithoutRequiredIdentifiers() {
        assertThatThrownBy(() -> projectInventoryService.getProjectSkills(null, 10L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_INVALID_INPUT));

        verifyNoInteractions(
                userProjectRepository,
                userProjectAnalysisRepository,
                userProjectSkillRepository,
                userProjectExperienceTagRepository
        );
    }

    private UserProjectAnalysis analysis(Long userProjectId, Long analysisId, int analysisVersion) {
        UserProject userProject = mock(UserProject.class);
        given(userProject.getId()).willReturn(userProjectId);

        UserProjectAnalysis analysis = mock(UserProjectAnalysis.class);
        given(analysis.getId()).willReturn(analysisId);
        given(analysis.getUserProject()).willReturn(userProject);
        given(analysis.getAnalysisVersion()).willReturn(analysisVersion);
        given(analysis.getAnalyzedAt()).willReturn(LocalDateTime.of(2026, 6, 15, 9, 0));

        return analysis;
    }

    private UserProjectSkill projectSkill(
            Long skillId,
            String name,
            String normalizedName,
            SkillCategory category,
            AnalysisSource source,
            String confidence,
            String evidence
    ) {
        Skill skill = mock(Skill.class);
        given(skill.getId()).willReturn(skillId);
        given(skill.getName()).willReturn(name);
        given(skill.getNormalizedName()).willReturn(normalizedName);
        given(skill.getCategory()).willReturn(category);

        UserProjectSkill projectSkill = mock(UserProjectSkill.class);
        given(projectSkill.getSkill()).willReturn(skill);
        given(projectSkill.getSource()).willReturn(source);
        given(projectSkill.getConfidence()).willReturn(new BigDecimal(confidence));
        given(projectSkill.getEvidence()).willReturn(evidence);

        return projectSkill;
    }

    private UserProjectExperienceTag experienceTag(
            String code,
            String name,
            String description,
            String confidence,
            String evidence
    ) {
        ExperienceTagCode tagCode = mock(ExperienceTagCode.class);
        given(tagCode.getCode()).willReturn(code);
        given(tagCode.getName()).willReturn(name);
        given(tagCode.getDescription()).willReturn(description);

        UserProjectExperienceTag projectExperienceTag = mock(UserProjectExperienceTag.class);
        given(projectExperienceTag.getTagCode()).willReturn(tagCode);
        given(projectExperienceTag.getConfidence()).willReturn(new BigDecimal(confidence));
        given(projectExperienceTag.getEvidence()).willReturn(evidence);

        return projectExperienceTag;
    }
}
