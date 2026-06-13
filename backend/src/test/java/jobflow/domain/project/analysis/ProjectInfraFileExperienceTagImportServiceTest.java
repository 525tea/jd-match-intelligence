package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jobflow.domain.project.UserProject;
import jobflow.domain.project.UserProjectAnalysis;
import jobflow.domain.project.UserProjectAnalysisRepository;
import jobflow.domain.project.UserProjectExperienceTag;
import jobflow.domain.project.UserProjectExperienceTagRepository;
import jobflow.domain.project.UserProjectRepository;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.ExperienceTagCodeRepository;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

class ProjectInfraFileExperienceTagImportServiceTest {

    private final UserProjectRepository userProjectRepository =
            mock(UserProjectRepository.class);

    private final UserProjectAnalysisRepository userProjectAnalysisRepository =
            mock(UserProjectAnalysisRepository.class);

    private final UserProjectExperienceTagRepository userProjectExperienceTagRepository =
            mock(UserProjectExperienceTagRepository.class);

    private final ExperienceTagCodeRepository experienceTagCodeRepository =
            mock(ExperienceTagCodeRepository.class);

    private final ProjectInfraFileAnalysisService projectInfraFileAnalysisService =
            mock(ProjectInfraFileAnalysisService.class);

    private final ProjectInfraFileExperienceTagImportService service =
            new ProjectInfraFileExperienceTagImportService(
                    userProjectRepository,
                    userProjectAnalysisRepository,
                    userProjectExperienceTagRepository,
                    experienceTagCodeRepository,
                    projectInfraFileAnalysisService
            );

    @Test
    @DisplayName("인프라 파일 분석 tag 후보를 기존 experience tag code와 매칭해 프로젝트 분석 결과로 저장한다")
    void importInfraFileExperienceTags() {
        Long userId = 1L;
        Long userProjectId = 10L;
        RepositoryRef repositoryRef = new RepositoryRef("525tea", "jobflow", "main");
        UserProject userProject = mock(UserProject.class);
        ExperienceTagCode cloudInfra = createExperienceTagCode("CLOUD_INFRA", "클라우드/인프라");
        ExperienceTagCode monitoring = createExperienceTagCode("MONITORING", "모니터링");
        List<UserProjectExperienceTag> savedProjectExperienceTags = new ArrayList<>();
        ProjectInfraFileAnalysisResult analysisResult = new ProjectInfraFileAnalysisResult(
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
                        InfraExperienceTagCandidate.of(
                                "UNKNOWN_TAG",
                                0.60,
                                "unknown evidence"
                        )
                )
        );
        given(userProjectRepository.findByIdAndUserId(userProjectId, userId))
                .willReturn(Optional.of(userProject));
        given(projectInfraFileAnalysisService.analyze(repositoryRef))
                .willReturn(analysisResult);
        given(experienceTagCodeRepository.findAllById(ArgumentMatchers.<Collection<String>>any()))
                .willReturn(List.of(cloudInfra, monitoring));
        given(userProjectAnalysisRepository.findMaxAnalysisVersionByUserProjectId(userProjectId))
                .willReturn(4);
        given(userProjectAnalysisRepository.save(any(UserProjectAnalysis.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(userProjectExperienceTagRepository.saveAll(ArgumentMatchers.<Iterable<UserProjectExperienceTag>>any()))
                .willAnswer(invocation -> {
                    Iterable<UserProjectExperienceTag> projectExperienceTags = invocation.getArgument(0);
                    projectExperienceTags.forEach(savedProjectExperienceTags::add);
                    return savedProjectExperienceTags;
                });

        ProjectInfraFileExperienceTagImportResult result = service.importInfraFileExperienceTags(
                userId,
                userProjectId,
                repositoryRef
        );

        assertThat(result.analysisVersion()).isEqualTo(5);
        assertThat(result.candidateTagCount()).isEqualTo(3);
        assertThat(result.savedTagCount()).isEqualTo(2);
        assertThat(result.savedTagCodes()).containsExactly("CLOUD_INFRA", "MONITORING");
        assertThat(result.unmappedTagCodes()).containsExactly("UNKNOWN_TAG");

        ArgumentCaptor<UserProjectAnalysis> analysisCaptor =
                ArgumentCaptor.forClass(UserProjectAnalysis.class);
        verify(userProjectAnalysisRepository).save(analysisCaptor.capture());
        assertThat(analysisCaptor.getValue().getAnalysisVersion()).isEqualTo(5);
        assertThat(analysisCaptor.getValue().getSourceHash()).hasSize(64);
        assertThat(analysisCaptor.getValue().getCommitSha()).isEqualTo("main");
        assertThat(analysisCaptor.getValue().getModelVersion()).isEqualTo("infra-file-static-v1");
        assertThat(analysisCaptor.getValue().getRawAnalysis()).contains("\"repository\":\"525tea/jobflow\"");
        assertThat(analysisCaptor.getValue().getRawAnalysis()).contains("\"experienceTagCandidates\"");

        verify(userProjectExperienceTagRepository).saveAll(ArgumentMatchers.<Iterable<UserProjectExperienceTag>>any());
        assertThat(savedProjectExperienceTags)
                .extracting(projectExperienceTag -> projectExperienceTag.getTagCode().getCode())
                .containsExactly("CLOUD_INFRA", "MONITORING");
    }

    @Test
    @DisplayName("프로젝트가 없거나 소유자가 다르면 USER_PROJECT_NOT_FOUND 예외를 던진다")
    void importInfraFileExperienceTagsWithoutOwnedProject() {
        Long userId = 1L;
        Long userProjectId = 10L;
        RepositoryRef repositoryRef = RepositoryRef.of("525tea", "jobflow");
        given(userProjectRepository.findByIdAndUserId(userProjectId, userId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.importInfraFileExperienceTags(userId, userProjectId, repositoryRef))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_PROJECT_NOT_FOUND);

        verifyNoInteractions(
                projectInfraFileAnalysisService,
                userProjectAnalysisRepository,
                userProjectExperienceTagRepository,
                experienceTagCodeRepository
        );
    }

    @Test
    @DisplayName("사용자나 프로젝트 식별자가 없으면 USER_PROJECT_NOT_FOUND 예외를 던진다")
    void importInfraFileExperienceTagsWithoutIdentifiers() {
        RepositoryRef repositoryRef = RepositoryRef.of("525tea", "jobflow");

        assertThatThrownBy(() -> service.importInfraFileExperienceTags(null, 10L, repositoryRef))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_PROJECT_NOT_FOUND);

        assertThatThrownBy(() -> service.importInfraFileExperienceTags(1L, null, repositoryRef))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_PROJECT_NOT_FOUND);

        verifyNoInteractions(
                userProjectRepository,
                projectInfraFileAnalysisService,
                userProjectAnalysisRepository,
                userProjectExperienceTagRepository,
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
