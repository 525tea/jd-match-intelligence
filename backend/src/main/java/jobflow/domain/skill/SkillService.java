package jobflow.domain.skill;

import java.util.List;
import jobflow.domain.skill.dto.SkillCreateRequest;
import jobflow.domain.skill.dto.SkillResponse;
import jobflow.domain.skill.dto.SkillUpdateRequest;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import jobflow.global.error.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkillService {

    private final SkillRepository skillRepository;

    public List<SkillResponse> findSkills(SkillCategory category, String keyword) {
        List<Skill> skills = findByCondition(category, keyword);

        return skills.stream()
                .map(SkillResponse::from)
                .toList();
    }

    @Transactional
    public SkillResponse createSkill(SkillCreateRequest request) {
        validateDuplicate(request.name(), request.normalizedName());

        Skill skill = Skill.create(
                request.name(),
                request.normalizedName(),
                request.category()
        );

        Skill savedSkill = skillRepository.save(skill);

        return SkillResponse.from(savedSkill);
    }

    @Transactional
    public SkillResponse updateSkill(Long skillId, SkillUpdateRequest request) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKILL_NOT_FOUND));

        validateDuplicateForUpdate(
                skillId,
                request.name(),
                request.normalizedName()
        );

        skill.update(
                request.name(),
                request.normalizedName(),
                request.category()
        );

        return SkillResponse.from(skill);
    }

    private List<Skill> findByCondition(SkillCategory category, String keyword) {
        boolean hasCategory = category != null;
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        if (hasCategory && hasKeyword) {
            return skillRepository.findByCategoryAndNameContainingIgnoreCaseOrderByNameAsc(
                    category,
                    keyword.trim()
            );
        }

        if (hasCategory) {
            return skillRepository.findByCategoryOrderByNameAsc(category);
        }

        if (hasKeyword) {
            return skillRepository.findByNameContainingIgnoreCaseOrderByNameAsc(keyword.trim());
        }

        return skillRepository.findAllByOrderByNameAsc();
    }

    private void validateDuplicate(String name, String normalizedName) {
        if (skillRepository.existsByName(name)
                || skillRepository.existsByNormalizedName(normalizedName)) {
            throw new ConflictException(ErrorCode.SKILL_ALREADY_EXISTS);
        }
    }

    private void validateDuplicateForUpdate(Long skillId, String name, String normalizedName) {
        if (skillRepository.existsByNameAndIdNot(name, skillId)
                || skillRepository.existsByNormalizedNameAndIdNot(normalizedName, skillId)) {
            throw new ConflictException(ErrorCode.SKILL_ALREADY_EXISTS);
        }
    }
}
