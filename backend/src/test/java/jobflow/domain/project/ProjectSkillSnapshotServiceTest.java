package jobflow.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectSkillSnapshotServiceTest {

    private final UserProjectRepository userProjectRepository =
            mock(UserProjectRepository.class);

    private final UserProjectAnalysisRepository userProjectAnalysisRepository =
            mock(UserProjectAnalysisRepository.class);

    private final UserProjectSkillRepository userProjectSkillRepository =
            mock(UserProjectSkillRepository.class);

    private final ProjectSkillSnapshotService projectSkillSnapshotService =
            new ProjectSkillSnapshotService(
                    userProjectRepository,
                    userProjectAnalysisRepository,
                    userProjectSkillRepository
            );

    @Test
    @DisplayName("사용자 프로젝트의 최신 분석 skill id 목록을 조회한다")
    void findLatestSkillIds() {
        Long userId = 1L;
        Long userProjectId = 10L;
        given(userProjectSkillRepository.findDistinctSkillIdsByLatestOwnedProjectAnalysis(userId, userProjectId))
                .willReturn(List.of(1L, 2L, 3L));

        List<Long> skillIds = projectSkillSnapshotService.findLatestSkillIds(userId, userProjectId);

        assertThat(skillIds).containsExactly(1L, 2L, 3L);
        verifyNoInteractions(userProjectRepository, userProjectAnalysisRepository);
    }

    @Test
    @DisplayName("최신 분석 결과가 없으면 빈 skill id 목록을 반환한다")
    void findLatestSkillIdsWithoutAnalysis() {
        Long userId = 1L;
        Long userProjectId = 10L;
        given(userProjectSkillRepository.findDistinctSkillIdsByLatestOwnedProjectAnalysis(userId, userProjectId))
                .willReturn(List.of());
        given(userProjectRepository.existsByIdAndUserId(userProjectId, userId))
                .willReturn(true);

        List<Long> skillIds = projectSkillSnapshotService.findLatestSkillIds(userId, userProjectId);

        assertThat(skillIds).isEmpty();
        verifyNoInteractions(userProjectAnalysisRepository);
    }

    @Test
    @DisplayName("사용자 프로젝트가 없거나 소유자가 다르면 USER_PROJECT_NOT_FOUND 예외를 던진다")
    void findLatestSkillIdsWithoutOwnedProject() {
        Long userId = 1L;
        Long userProjectId = 10L;
        given(userProjectSkillRepository.findDistinctSkillIdsByLatestOwnedProjectAnalysis(userId, userProjectId))
                .willReturn(List.of());
        given(userProjectRepository.existsByIdAndUserId(userProjectId, userId))
                .willReturn(false);

        assertThatThrownBy(() -> projectSkillSnapshotService.findLatestSkillIds(userId, userProjectId))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_PROJECT_NOT_FOUND);

        verifyNoInteractions(userProjectAnalysisRepository);
    }

    @Test
    @DisplayName("사용자나 프로젝트 식별자가 없으면 repository를 조회하지 않는다")
    void findLatestSkillIdsWithoutIdentifiers() {
        assertThat(projectSkillSnapshotService.findLatestSkillIds(null, 10L)).isEmpty();
        assertThat(projectSkillSnapshotService.findLatestSkillIds(1L, null)).isEmpty();

        verify(userProjectSkillRepository, never()).findDistinctSkillIdsByLatestOwnedProjectAnalysis(1L, 10L);
        verifyNoInteractions(userProjectRepository, userProjectAnalysisRepository);
    }
}
