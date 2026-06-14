package jobflow.domain.project;

import java.util.List;
import jobflow.domain.project.dto.ProjectExperienceTagInventoryResponse;
import jobflow.domain.project.dto.ProjectSkillInventoryResponse;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectInventoryService {

    private final UserProjectRepository userProjectRepository;
    private final UserProjectAnalysisRepository userProjectAnalysisRepository;
    private final UserProjectSkillRepository userProjectSkillRepository;
    private final UserProjectExperienceTagRepository userProjectExperienceTagRepository;

    public List<ProjectSkillInventoryResponse> getProjectSkills(Long userId, Long userProjectId) {
        validateOwnedProject(userId, userProjectId);

        return userProjectAnalysisRepository
                .findFirstByUserProjectIdAndUserProjectUserIdOrderByAnalyzedAtDescIdDesc(userProjectId, userId)
                .map(analysis -> userProjectSkillRepository.findByAnalysisIdWithSkill(analysis.getId())
                        .stream()
                        .map(projectSkill -> ProjectSkillInventoryResponse.from(analysis, projectSkill))
                        .toList())
                .orElseGet(List::of);
    }

    public List<ProjectExperienceTagInventoryResponse> getProjectExperienceTags(
            Long userId,
            Long userProjectId
    ) {
        validateOwnedProject(userId, userProjectId);

        return userProjectAnalysisRepository
                .findFirstByUserProjectIdAndUserProjectUserIdOrderByAnalyzedAtDescIdDesc(userProjectId, userId)
                .map(analysis -> userProjectExperienceTagRepository.findByAnalysisIdWithTagCode(analysis.getId())
                        .stream()
                        .map(projectExperienceTag -> ProjectExperienceTagInventoryResponse.from(
                                analysis,
                                projectExperienceTag
                        ))
                        .toList())
                .orElseGet(List::of);
    }

    private void validateOwnedProject(Long userId, Long userProjectId) {
        if (userId == null || userProjectId == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_INVALID_INPUT,
                    "사용자와 프로젝트 식별자는 필수입니다."
            );
        }

        if (!userProjectRepository.existsByIdAndUserId(userProjectId, userId)) {
            throw new BusinessException(
                    ErrorCode.USER_PROJECT_NOT_FOUND,
                    "사용자 프로젝트를 찾을 수 없습니다."
            );
        }
    }
}
