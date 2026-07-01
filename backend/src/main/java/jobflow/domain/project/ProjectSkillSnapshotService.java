package jobflow.domain.project;

import java.util.List;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectSkillSnapshotService {

    private final UserProjectRepository userProjectRepository;
    private final UserProjectAnalysisRepository userProjectAnalysisRepository;
    private final UserProjectSkillRepository userProjectSkillRepository;

    public ProjectSkillSnapshotService(
            UserProjectRepository userProjectRepository,
            UserProjectAnalysisRepository userProjectAnalysisRepository,
            UserProjectSkillRepository userProjectSkillRepository
    ) {
        this.userProjectRepository = userProjectRepository;
        this.userProjectAnalysisRepository = userProjectAnalysisRepository;
        this.userProjectSkillRepository = userProjectSkillRepository;
    }

    public List<Long> findLatestSkillIds(Long userId, Long userProjectId) {
        if (userId == null || userProjectId == null) {
            return List.of();
        }

        List<Long> skillIds = userProjectSkillRepository.findDistinctSkillIdsByLatestOwnedProjectAnalysis(
                userId,
                userProjectId
        );
        if (!skillIds.isEmpty()) {
            return skillIds;
        }

        if (!userProjectRepository.existsByIdAndUserId(userProjectId, userId)) {
            throw new EntityNotFoundException(ErrorCode.USER_PROJECT_NOT_FOUND);
        }

        return List.of();
    }
}
