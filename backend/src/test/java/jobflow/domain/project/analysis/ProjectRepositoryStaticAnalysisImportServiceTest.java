package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jobflow.domain.project.ProjectAnalysisUpdatedEvent;
import jobflow.domain.project.UserProject;
import jobflow.domain.project.UserProjectAnalysis;
import jobflow.domain.project.UserProjectAnalysisRepository;
import jobflow.domain.project.UserProjectExperienceTag;
import jobflow.domain.project.UserProjectExperienceTagRepository;
import jobflow.domain.project.UserProjectRepository;
import jobflow.domain.project.UserProjectSkill;
import jobflow.domain.project.UserProjectSkillRepository;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.ExperienceTagCodeRepository;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillCategory;
import jobflow.domain.skill.SkillRepository;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.context.ApplicationEventPublisher;

class ProjectRepositoryStaticAnalysisImportServiceTest {

    private final UserProjectRepository userProjectRepository =
            mock(UserProjectRepository.class);

    private final UserProjectAnalysisRepository userProjectAnalysisRepository =
            mock(UserProjectAnalysisRepository.class);

    private final UserProjectSkillRepository userProjectSkillRepository =
            mock(UserProjectSkillRepository.class);

    private final UserProjectExperienceTagRepository userProjectExperienceTagRepository =
            mock(UserProjectExperienceTagRepository.class);

    private final ApplicationEventPublisher eventPublisher =
            mock(ApplicationEventPublisher.class);

    private final SkillRepository skillRepository =
            mock(SkillRepository.class);

    private final ExperienceTagCodeRepository experienceTagCodeRepository =
            mock(ExperienceTagCodeRepository.class);

    private final ProjectBuildFileAnalysisService projectBuildFileAnalysisService =
            mock(ProjectBuildFileAnalysisService.class);

    private final ProjectInfraFileAnalysisService projectInfraFileAnalysisService =
            mock(ProjectInfraFileAnalysisService.class);

    private final ProjectWorkflowFileAnalysisService projectWorkflowFileAnalysisService =
            mock(ProjectWorkflowFileAnalysisService.class);

    private final ProjectRepositoryStaticAnalysisImportService service =
            new ProjectRepositoryStaticAnalysisImportService(
                    userProjectRepository,
                    userProjectAnalysisRepository,
                    userProjectSkillRepository,
                    userProjectExperienceTagRepository,
                    eventPublisher,
                    skillRepository,
                    experienceTagCodeRepository,
                    projectBuildFileAnalysisService,
                    projectInfraFileAnalysisService,
                    projectWorkflowFileAnalysisService
            );

    @Test
    @DisplayName("빌드 파일 skill과 인프라 파일 experience tag를 하나의 프로젝트 분석 버전에 함께 저장한다")
    void importRepositoryStaticAnalysis() {
        Long userId = 1L;
        Long userProjectId = 10L;
        RepositoryRef repositoryRef = new RepositoryRef("example-org", "sample-repo", "main");
        UserProject userProject = mock(UserProject.class);
        Skill java = Skill.create("Java", "java", SkillCategory.LANGUAGE);
        Skill springBoot = Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK);
        ExperienceTagCode cloudInfra = createExperienceTagCode("CLOUD_INFRA", "클라우드/인프라");
        ExperienceTagCode monitoring = createExperienceTagCode("MONITORING", "모니터링");
        ExperienceTagCode ciCd = createExperienceTagCode("CI_CD", "CI/CD");
        List<UserProjectSkill> savedProjectSkills = new ArrayList<>();
        List<UserProjectExperienceTag> savedProjectExperienceTags = new ArrayList<>();
        ProjectBuildFileAnalysisResult buildFileAnalysis = new ProjectBuildFileAnalysisResult(
                repositoryRef,
                3,
                2,
                List.of("backend/build.gradle", "frontend/package.json"),
                List.of(
                        BuildFileSkillCandidate.of("Java", "plugin:java", 0.90),
                        BuildFileSkillCandidate.of(
                                "Spring Boot",
                                "gradle dependency org.springframework.boot:spring-boot-starter-web",
                                0.95
                        ),
                        BuildFileSkillCandidate.of("Next.js", "package.json dependency next", 0.95)
                )
        );
        ProjectInfraFileAnalysisResult infraFileAnalysis = new ProjectInfraFileAnalysisResult(
                repositoryRef,
                4,
                3,
                List.of("Dockerfile", "docker-compose.yml", "application.yml"),
                List.of(
                        InfraExperienceTagCandidate.of(
                                "CLOUD_INFRA",
                                0.85,
                                "docker-compose.yml: docker compose local infrastructure orchestration"
                        ),
                        InfraExperienceTagCandidate.of(
                                "MONITORING",
                                0.85,
                                "application.yml: management.endpoints.web.exposure.include: health,metrics,prometheus"
                        ),
                        InfraExperienceTagCandidate.of("UNKNOWN_TAG", 0.60, "unknown evidence")
                )
        );
        ProjectWorkflowFileAnalysisResult workflowFileAnalysis = new ProjectWorkflowFileAnalysisResult(
                repositoryRef,
                2,
                1,
                List.of(".github/workflows/backend-ci.yml"),
                List.of(
                        WorkflowExperienceTagCandidate.of(
                                "CI_CD",
                                0.95,
                                ".github/workflows/backend-ci.yml: GitHub Actions workflow"
                        ),
                        WorkflowExperienceTagCandidate.of(
                                "TESTING",
                                0.85,
                                ".github/workflows/backend-ci.yml: - run: ./gradlew :backend:test"
                        )
                )
        );
        given(userProjectRepository.findByIdAndUserId(userProjectId, userId))
                .willReturn(Optional.of(userProject));
        given(projectBuildFileAnalysisService.analyze(userId, repositoryRef))
                .willReturn(buildFileAnalysis);
        given(projectInfraFileAnalysisService.analyze(userId, repositoryRef))
                .willReturn(infraFileAnalysis);
        given(projectWorkflowFileAnalysisService.analyze(userId, repositoryRef))
                .willReturn(workflowFileAnalysis);
        given(skillRepository.findByNameIn(ArgumentMatchers.<Collection<String>>any()))
                .willReturn(List.of(java, springBoot));
        given(experienceTagCodeRepository.findAllById(ArgumentMatchers.<Collection<String>>any()))
                .willReturn(List.of(cloudInfra, monitoring, ciCd));
        given(userProjectAnalysisRepository
                .findFirstByUserProjectIdAndUserProjectUserIdAndModelVersionOrderByAnalyzedAtDescIdDesc(
                        userProjectId,
                        userId,
                        "repository-static-v2"
                ))
                .willReturn(Optional.empty());
        given(userProjectAnalysisRepository.findMaxAnalysisVersionByUserProjectId(userProjectId))
                .willReturn(7);
        given(userProjectAnalysisRepository.save(any(UserProjectAnalysis.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(userProjectSkillRepository.saveAll(ArgumentMatchers.<Iterable<UserProjectSkill>>any()))
                .willAnswer(invocation -> {
                    Iterable<UserProjectSkill> projectSkills = invocation.getArgument(0);
                    projectSkills.forEach(savedProjectSkills::add);
                    return savedProjectSkills;
                });
        given(userProjectExperienceTagRepository.saveAll(
                ArgumentMatchers.<Iterable<UserProjectExperienceTag>>any()
        ))
                .willAnswer(invocation -> {
                    Iterable<UserProjectExperienceTag> projectExperienceTags = invocation.getArgument(0);
                    projectExperienceTags.forEach(savedProjectExperienceTags::add);
                    return savedProjectExperienceTags;
                });

        ProjectRepositoryStaticAnalysisImportResult result = service.importRepositoryStaticAnalysis(
                userId,
                userProjectId,
                repositoryRef
        );

        assertThat(result.analysisVersion()).isEqualTo(8);
        assertThat(result.skipped()).isFalse();
        assertThat(result.candidateSkillCount()).isEqualTo(3);
        assertThat(result.savedSkillCount()).isEqualTo(2);
        assertThat(result.savedSkillNames()).containsExactly("Java", "Spring Boot");
        assertThat(result.unmappedSkillNames()).containsExactly("Next.js");
        assertThat(result.candidateTagCount()).isEqualTo(5);
        assertThat(result.savedTagCount()).isEqualTo(3);
        assertThat(result.savedTagCodes()).containsExactly("CI_CD", "CLOUD_INFRA", "MONITORING");
        assertThat(result.unmappedTagCodes()).containsExactly("TESTING", "UNKNOWN_TAG");

        ArgumentCaptor<UserProjectAnalysis> analysisCaptor =
                ArgumentCaptor.forClass(UserProjectAnalysis.class);
        verify(userProjectAnalysisRepository).save(analysisCaptor.capture());
        UserProjectAnalysis savedAnalysis = analysisCaptor.getValue();
        assertThat(savedAnalysis.getAnalysisVersion()).isEqualTo(8);
        assertThat(savedAnalysis.getSourceHash()).hasSize(64);
        assertThat(savedAnalysis.getCommitSha()).isEqualTo("main");
        assertThat(savedAnalysis.getModelVersion()).isEqualTo("repository-static-v2");
        assertThat(savedAnalysis.getRawAnalysis()).contains("\"repository\":\"example-org/sample-repo\"");
        assertThat(savedAnalysis.getRawAnalysis()).contains("\"buildFileAnalysis\"");
        assertThat(savedAnalysis.getRawAnalysis()).contains("\"infraFileAnalysis\"");
        assertThat(savedAnalysis.getRawAnalysis()).contains("\"workflowFileAnalysis\"");
        assertThat(savedAnalysis.getRawAnalysis()).contains(".github/workflows/backend-ci.yml");
        assertThat(savedAnalysis.getRawAnalysis()).contains("\"skillCandidates\"");
        assertThat(savedAnalysis.getRawAnalysis()).contains("\"experienceTagCandidates\"");

        verify(userProjectSkillRepository).saveAll(ArgumentMatchers.<Iterable<UserProjectSkill>>any());
        verify(userProjectExperienceTagRepository)
                .saveAll(ArgumentMatchers.<Iterable<UserProjectExperienceTag>>any());
        verify(eventPublisher).publishEvent(new ProjectAnalysisUpdatedEvent(userId, userProjectId));
        assertThat(savedProjectSkills)
                .extracting(UserProjectSkill::getAnalysis)
                .containsOnly(savedAnalysis);
        assertThat(savedProjectExperienceTags)
                .extracting(UserProjectExperienceTag::getAnalysis)
                .containsOnly(savedAnalysis);
    }

    @Test
    @DisplayName("source hash가 최신 분석과 같으면 새 분석 버전을 저장하지 않는다")
    void skipImportWhenLatestSourceHashIsUnchanged() {
        Long userId = 1L;
        Long userProjectId = 10L;
        RepositoryRef repositoryRef = new RepositoryRef("example-org", "sample-repo", "main");
        UserProject userProject = mock(UserProject.class);
        Skill java = Skill.create("Java", "java", SkillCategory.LANGUAGE);
        ExperienceTagCode cloudInfra = createExperienceTagCode("CLOUD_INFRA", "클라우드/인프라");
        ExperienceTagCode ciCd = createExperienceTagCode("CI_CD", "CI/CD");
        List<UserProjectSkill> savedProjectSkills = new ArrayList<>();
        List<UserProjectExperienceTag> savedProjectExperienceTags = new ArrayList<>();
        ProjectBuildFileAnalysisResult buildFileAnalysis = new ProjectBuildFileAnalysisResult(
                repositoryRef,
                1,
                1,
                List.of("backend/build.gradle"),
                List.of(BuildFileSkillCandidate.of("Java", "plugin:java", 0.90))
        );
        ProjectInfraFileAnalysisResult infraFileAnalysis = new ProjectInfraFileAnalysisResult(
                repositoryRef,
                1,
                1,
                List.of("Dockerfile"),
                List.of(InfraExperienceTagCandidate.of(
                        "CLOUD_INFRA",
                        0.85,
                        "Dockerfile: FROM eclipse-temurin"
                ))
        );
        ProjectWorkflowFileAnalysisResult workflowFileAnalysis = new ProjectWorkflowFileAnalysisResult(
                repositoryRef,
                1,
                1,
                List.of(".github/workflows/backend-ci.yml"),
                List.of(WorkflowExperienceTagCandidate.of(
                        "CI_CD",
                        0.95,
                        ".github/workflows/backend-ci.yml: GitHub Actions workflow"
                ))
        );
        given(userProjectRepository.findByIdAndUserId(userProjectId, userId))
                .willReturn(Optional.of(userProject));
        given(projectBuildFileAnalysisService.analyze(userId, repositoryRef))
                .willReturn(buildFileAnalysis);
        given(projectInfraFileAnalysisService.analyze(userId, repositoryRef))
                .willReturn(infraFileAnalysis);
        given(projectWorkflowFileAnalysisService.analyze(userId, repositoryRef))
                .willReturn(workflowFileAnalysis);
        given(skillRepository.findByNameIn(ArgumentMatchers.<Collection<String>>any()))
                .willReturn(List.of(java));
        given(experienceTagCodeRepository.findAllById(ArgumentMatchers.<Collection<String>>any()))
                .willReturn(List.of(cloudInfra, ciCd));
        given(userProjectAnalysisRepository
                .findFirstByUserProjectIdAndUserProjectUserIdAndModelVersionOrderByAnalyzedAtDescIdDesc(
                        userProjectId,
                        userId,
                        "repository-static-v2"
                ))
                .willReturn(Optional.empty());
        given(userProjectAnalysisRepository.findMaxAnalysisVersionByUserProjectId(userProjectId))
                .willReturn(8);
        given(userProjectAnalysisRepository.save(any(UserProjectAnalysis.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(userProjectSkillRepository.saveAll(ArgumentMatchers.<Iterable<UserProjectSkill>>any()))
                .willAnswer(invocation -> {
                    Iterable<UserProjectSkill> projectSkills = invocation.getArgument(0);
                    projectSkills.forEach(savedProjectSkills::add);
                    return savedProjectSkills;
                });
        given(userProjectExperienceTagRepository.saveAll(
                ArgumentMatchers.<Iterable<UserProjectExperienceTag>>any()
        ))
                .willAnswer(invocation -> {
                    Iterable<UserProjectExperienceTag> projectExperienceTags = invocation.getArgument(0);
                    projectExperienceTags.forEach(savedProjectExperienceTags::add);
                    return savedProjectExperienceTags;
                });

        ProjectRepositoryStaticAnalysisImportResult firstResult = service.importRepositoryStaticAnalysis(
                userId,
                userProjectId,
                repositoryRef
        );
        ArgumentCaptor<UserProjectAnalysis> firstAnalysisCaptor =
                ArgumentCaptor.forClass(UserProjectAnalysis.class);
        verify(userProjectAnalysisRepository).save(firstAnalysisCaptor.capture());
        UserProjectAnalysis firstAnalysis = firstAnalysisCaptor.getValue();
        UserProjectAnalysis latestAnalysis = UserProjectAnalysis.create(
                userProject,
                firstAnalysis.getAnalysisVersion(),
                firstAnalysis.getSourceHash(),
                firstAnalysis.getCommitSha(),
                firstAnalysis.getModelVersion(),
                firstAnalysis.getRawAnalysis(),
                BigDecimal.ONE,
                LocalDateTime.now()
        );
        given(userProjectAnalysisRepository
                .findFirstByUserProjectIdAndUserProjectUserIdAndModelVersionOrderByAnalyzedAtDescIdDesc(
                        userProjectId,
                        userId,
                        "repository-static-v2"
                ))
                .willReturn(Optional.of(latestAnalysis));
        clearInvocations(
                userProjectAnalysisRepository,
                userProjectSkillRepository,
                userProjectExperienceTagRepository,
                eventPublisher,
                skillRepository,
                experienceTagCodeRepository
        );

        ProjectRepositoryStaticAnalysisImportResult secondResult = service.importRepositoryStaticAnalysis(
                userId,
                userProjectId,
                repositoryRef
        );

        assertThat(firstResult.skipped()).isFalse();
        assertThat(secondResult.skipped()).isTrue();
        assertThat(secondResult.analysisVersion()).isEqualTo(firstAnalysis.getAnalysisVersion());
        verify(userProjectAnalysisRepository, never()).findMaxAnalysisVersionByUserProjectId(userProjectId);
        verify(userProjectAnalysisRepository, never()).save(any(UserProjectAnalysis.class));
        verify(userProjectSkillRepository, never()).saveAll(ArgumentMatchers.<Iterable<UserProjectSkill>>any());
        verify(userProjectExperienceTagRepository, never())
                .saveAll(ArgumentMatchers.<Iterable<UserProjectExperienceTag>>any());
        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(skillRepository, experienceTagCodeRepository);
    }

    @Test
    @DisplayName("프로젝트가 없거나 소유자가 다르면 USER_PROJECT_NOT_FOUND 예외를 던진다")
    void importRepositoryStaticAnalysisWithoutOwnedProject() {
        Long userId = 1L;
        Long userProjectId = 10L;
        RepositoryRef repositoryRef = RepositoryRef.of("example-org", "sample-repo");
        given(userProjectRepository.findByIdAndUserId(userProjectId, userId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.importRepositoryStaticAnalysis(userId, userProjectId, repositoryRef))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_PROJECT_NOT_FOUND);

        verifyNoInteractions(
                projectBuildFileAnalysisService,
                projectInfraFileAnalysisService,
                projectWorkflowFileAnalysisService,
                userProjectAnalysisRepository,
                userProjectSkillRepository,
                userProjectExperienceTagRepository,
                eventPublisher,
                skillRepository,
                experienceTagCodeRepository
        );
    }

    @Test
    @DisplayName("사용자나 프로젝트 식별자가 없으면 USER_PROJECT_NOT_FOUND 예외를 던진다")
    void importRepositoryStaticAnalysisWithoutIdentifiers() {
        RepositoryRef repositoryRef = RepositoryRef.of("example-org", "sample-repo");

        assertThatThrownBy(() -> service.importRepositoryStaticAnalysis(null, 10L, repositoryRef))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_PROJECT_NOT_FOUND);

        assertThatThrownBy(() -> service.importRepositoryStaticAnalysis(1L, null, repositoryRef))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_PROJECT_NOT_FOUND);

        verifyNoInteractions(
                userProjectRepository,
                projectBuildFileAnalysisService,
                projectInfraFileAnalysisService,
                projectWorkflowFileAnalysisService,
                userProjectAnalysisRepository,
                userProjectSkillRepository,
                userProjectExperienceTagRepository,
                eventPublisher,
                skillRepository,
                experienceTagCodeRepository
        );
    }

    private ExperienceTagCode createExperienceTagCode(String code, String name) {
        try {
            Constructor<ExperienceTagCode> constructor = ExperienceTagCode.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ExperienceTagCode tagCode = constructor.newInstance();
            setField(tagCode, "code", code);
            setField(tagCode, "name", name);
            setField(tagCode, "description", name + " 경험");
            setField(tagCode, "createdAt", LocalDateTime.now());
            return tagCode;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create ExperienceTagCode for test", exception);
        }
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
