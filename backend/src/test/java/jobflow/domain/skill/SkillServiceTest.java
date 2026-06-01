package jobflow.domain.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import jobflow.domain.skill.dto.SkillCreateRequest;
import jobflow.domain.skill.dto.SkillResponse;
import jobflow.domain.skill.dto.SkillUpdateRequest;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import jobflow.global.error.exception.ConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @InjectMocks
    private SkillService skillService;

    @Test
    @DisplayName("스킬 목록을 전체 조회한다")
    void findSkills() {
        Skill java = createSkill(1L, "Java", "java", SkillCategory.LANGUAGE);
        Skill springBoot = createSkill(2L, "Spring Boot", "spring boot", SkillCategory.FRAMEWORK);

        given(skillRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(java, springBoot));

        List<SkillResponse> responses = skillService.findSkills(null, null);

        assertThat(responses).hasSize(2);
        assertThat(responses)
                .extracting(SkillResponse::name)
                .containsExactly("Java", "Spring Boot");

        verify(skillRepository).findAllByOrderByNameAsc();
    }

    @Test
    @DisplayName("카테고리로 스킬 목록을 조회한다")
    void findSkillsByCategory() {
        Skill springBoot = createSkill(1L, "Spring Boot", "spring boot", SkillCategory.FRAMEWORK);

        given(skillRepository.findByCategoryOrderByNameAsc(SkillCategory.FRAMEWORK))
                .willReturn(List.of(springBoot));

        List<SkillResponse> responses = skillService.findSkills(SkillCategory.FRAMEWORK, null);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().category()).isEqualTo(SkillCategory.FRAMEWORK);

        verify(skillRepository).findByCategoryOrderByNameAsc(SkillCategory.FRAMEWORK);
    }

    @Test
    @DisplayName("키워드로 스킬 목록을 조회한다")
    void findSkillsByKeyword() {
        Skill springBoot = createSkill(1L, "Spring Boot", "spring boot", SkillCategory.FRAMEWORK);

        given(skillRepository.findByNameContainingIgnoreCaseOrderByNameAsc("spring"))
                .willReturn(List.of(springBoot));

        List<SkillResponse> responses = skillService.findSkills(null, " spring ");

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().name()).isEqualTo("Spring Boot");

        verify(skillRepository).findByNameContainingIgnoreCaseOrderByNameAsc("spring");
    }

    @Test
    @DisplayName("카테고리와 키워드로 스킬 목록을 조회한다")
    void findSkillsByCategoryAndKeyword() {
        Skill springBoot = createSkill(1L, "Spring Boot", "spring boot", SkillCategory.FRAMEWORK);

        given(skillRepository.findByCategoryAndNameContainingIgnoreCaseOrderByNameAsc(
                SkillCategory.FRAMEWORK,
                "spring"
        )).willReturn(List.of(springBoot));

        List<SkillResponse> responses = skillService.findSkills(SkillCategory.FRAMEWORK, " spring ");

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().name()).isEqualTo("Spring Boot");

        verify(skillRepository).findByCategoryAndNameContainingIgnoreCaseOrderByNameAsc(
                SkillCategory.FRAMEWORK,
                "spring"
        );
    }

    @Test
    @DisplayName("스킬을 등록한다")
    void createSkill() {
        SkillCreateRequest request = new SkillCreateRequest(
                "Spring Boot",
                "spring boot",
                SkillCategory.FRAMEWORK
        );

        Skill savedSkill = createSkill(1L, request.name(), request.normalizedName(), request.category());

        given(skillRepository.existsByName(request.name())).willReturn(false);
        given(skillRepository.existsByNormalizedName(request.normalizedName())).willReturn(false);
        given(skillRepository.save(org.mockito.ArgumentMatchers.any(Skill.class))).willReturn(savedSkill);

        SkillResponse response = skillService.createSkill(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Spring Boot");
        assertThat(response.normalizedName()).isEqualTo("spring boot");
        assertThat(response.category()).isEqualTo(SkillCategory.FRAMEWORK);
    }

    @Test
    @DisplayName("이미 존재하는 이름으로 스킬을 등록하면 예외가 발생한다")
    void createSkillWithDuplicatedName() {
        SkillCreateRequest request = new SkillCreateRequest(
                "Spring Boot",
                "spring boot",
                SkillCategory.FRAMEWORK
        );

        given(skillRepository.existsByName(request.name())).willReturn(true);

        assertThatThrownBy(() -> skillService.createSkill(request))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SKILL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("스킬을 수정한다")
    void updateSkill() {
        Long skillId = 1L;
        Skill skill = createSkill(skillId, "Spring", "spring", SkillCategory.FRAMEWORK);

        SkillUpdateRequest request = new SkillUpdateRequest(
                "Spring Boot",
                "spring boot",
                SkillCategory.FRAMEWORK
        );

        given(skillRepository.findById(skillId)).willReturn(Optional.of(skill));
        given(skillRepository.existsByNameAndIdNot(request.name(), skillId)).willReturn(false);
        given(skillRepository.existsByNormalizedNameAndIdNot(request.normalizedName(), skillId)).willReturn(false);

        SkillResponse response = skillService.updateSkill(skillId, request);

        assertThat(response.id()).isEqualTo(skillId);
        assertThat(response.name()).isEqualTo("Spring Boot");
        assertThat(response.normalizedName()).isEqualTo("spring boot");
        assertThat(response.category()).isEqualTo(SkillCategory.FRAMEWORK);
    }

    @Test
    @DisplayName("존재하지 않는 스킬을 수정하면 예외가 발생한다")
    void updateMissingSkill() {
        Long skillId = 999L;
        SkillUpdateRequest request = new SkillUpdateRequest(
                "Spring Boot",
                "spring boot",
                SkillCategory.FRAMEWORK
        );

        given(skillRepository.findById(skillId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> skillService.updateSkill(skillId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SKILL_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 존재하는 이름으로 스킬을 수정하면 예외가 발생한다")
    void updateSkillWithDuplicatedName() {
        Long skillId = 1L;
        Skill skill = createSkill(skillId, "Spring", "spring", SkillCategory.FRAMEWORK);

        SkillUpdateRequest request = new SkillUpdateRequest(
                "Java",
                "java",
                SkillCategory.LANGUAGE
        );

        given(skillRepository.findById(skillId)).willReturn(Optional.of(skill));
        given(skillRepository.existsByNameAndIdNot(request.name(), skillId)).willReturn(true);

        assertThatThrownBy(() -> skillService.updateSkill(skillId, request))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SKILL_ALREADY_EXISTS);
    }

    private Skill createSkill(
            Long id,
            String name,
            String normalizedName,
            SkillCategory category
    ) {
        Skill skill = Skill.create(name, normalizedName, category);
        ReflectionTestUtils.setField(skill, "id", id);
        return skill;
    }
}
