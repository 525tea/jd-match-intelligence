package jobflow.domain.project;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectSkillSnapshotService {

    private final UserProjectAnalysisRepository userProjectAnalysisRepository;
    private final UserProjectSkillRepository userProjectSkillRepository;

    public ProjectSkillSnapshotService(
            UserProjectAnalysisRepository userProjectAnalysisRepository,
            UserProjectSkillRepository userProjectSkillRepository
    ) {
        this.userProjectAnalysisRepository = userProjectAnalysisRepository;
        this.userProjectSkillRepository = userProjectSkillRepository;
    }

    public List<Long> findLatestSkillIds(Long userId, Long userProjectId) {
        if (userId == null || userProjectId == null) {
            return List.of();
        }

        return userProjectAnalysisRepository
                .findFirstByUserProjectIdAndUserProjectUserIdOrderByAnalyzedAtDescIdDesc(userProjectId, userId)
                .map(UserProjectAnalysis::getId)
                .map(userProjectSkillRepository::findDistinctSkillIdsByAnalysisId)
                .orElseGet(List::of);
    }
}
