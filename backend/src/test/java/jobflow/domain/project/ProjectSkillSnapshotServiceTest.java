package jobflow.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectSkillSnapshotServiceTest {

    private final UserProjectAnalysisRepository userProjectAnalysisRepository =
            mock(UserProjectAnalysisRepository.class);

    private final UserProjectSkillRepository userProjectSkillRepository =
            mock(UserProjectSkillRepository.class);

    private final ProjectSkillSnapshotService projectSkillSnapshotService =
            new ProjectSkillSnapshotService(userProjectAnalysisRepository, userProjectSkillRepository);

    @Test
    @DisplayName("사용자 프로젝트의 최신 분석 skill id 목록을 조회한다")
    void findLatestSkillIds() {
        Long userId = 1L;
        Long userProjectId = 10L;
        UserProjectAnalysis analysis = mock(UserProjectAnalysis.class);
        given(analysis.getId()).willReturn(100L);
        given(userProjectAnalysisRepository
                .findFirstByUserProjectIdAndUserProjectUserIdOrderByAnalyzedAtDescIdDesc(userProjectId, userId)
        ).willReturn(Optional.of(analysis));
        given(userProjectSkillRepository.findDistinctSkillIdsByAnalysisId(100L))
                .willReturn(List.of(1L, 2L, 3L));

        List<Long> skillIds = projectSkillSnapshotService.findLatestSkillIds(userId, userProjectId);

        assertThat(skillIds).containsExactly(1L, 2L, 3L);
        verify(userProjectSkillRepository).findDistinctSkillIdsByAnalysisId(100L);
    }

    @Test
    @DisplayName("최신 분석 결과가 없으면 빈 skill id 목록을 반환한다")
    void findLatestSkillIdsWithoutAnalysis() {
        Long userId = 1L;
        Long userProjectId = 10L;
        given(userProjectAnalysisRepository
                .findFirstByUserProjectIdAndUserProjectUserIdOrderByAnalyzedAtDescIdDesc(userProjectId, userId)
        ).willReturn(Optional.empty());

        List<Long> skillIds = projectSkillSnapshotService.findLatestSkillIds(userId, userProjectId);

        assertThat(skillIds).isEmpty();
        verifyNoInteractions(userProjectSkillRepository);
    }

    @Test
    @DisplayName("사용자나 프로젝트 식별자가 없으면 repository를 조회하지 않는다")
    void findLatestSkillIdsWithoutIdentifiers() {
        assertThat(projectSkillSnapshotService.findLatestSkillIds(null, 10L)).isEmpty();
        assertThat(projectSkillSnapshotService.findLatestSkillIds(1L, null)).isEmpty();

        verifyNoInteractions(userProjectAnalysisRepository, userProjectSkillRepository);
    }
}
