package jobflow.domain.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class SkillAliasRepositoryTest {

    @Autowired
    private SkillAliasRepository skillAliasRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Test
    @DisplayName("활성화된 alias를 normalized alias로 조회한다")
    void findByNormalizedAliasAndEnabledTrue() {
        Skill skill = skillRepository.save(Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK));
        skillAliasRepository.save(SkillAlias.create(
                skill,
                "SpringBoot",
                "springboot",
                BigDecimal.valueOf(0.9500)
        ));

        SkillAlias skillAlias = skillAliasRepository.findByNormalizedAliasAndEnabledTrue("springboot")
                .orElseThrow();

        assertThat(skillAlias.getSkill().getNormalizedName()).isEqualTo("spring boot");
        assertThat(skillAlias.getAlias()).isEqualTo("SpringBoot");
        assertThat(skillAlias.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("비활성화된 alias는 활성 alias 조회에서 제외한다")
    void findByNormalizedAliasAndEnabledTrueExcludesDisabledAlias() {
        Skill skill = skillRepository.save(Skill.create("Kubernetes", "kubernetes", SkillCategory.INFRA));
        SkillAlias skillAlias = skillAliasRepository.save(SkillAlias.create(
                skill,
                "k8s",
                "k8s",
                BigDecimal.valueOf(0.9500)
        ));
        skillAlias.disable();

        assertThat(skillAliasRepository.findByNormalizedAliasAndEnabledTrue("k8s")).isEmpty();
    }

    @Test
    @DisplayName("활성화된 alias 목록을 normalized alias 기준으로 조회한다")
    void findByEnabledTrueOrderByNormalizedAliasAsc() {
        Skill javaScript = skillRepository.save(Skill.create("JavaScript", "javascript", SkillCategory.LANGUAGE));
        Skill springBoot = skillRepository.save(Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK));

        skillAliasRepository.save(SkillAlias.create(
                springBoot,
                "SpringBoot",
                "springboot",
                BigDecimal.valueOf(0.9500)
        ));
        skillAliasRepository.save(SkillAlias.create(
                javaScript,
                "js",
                "js",
                BigDecimal.valueOf(0.9000)
        ));

        List<SkillAlias> skillAliases = skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc();

        assertThat(skillAliases)
                .extracting(SkillAlias::getNormalizedAlias)
                .containsExactly("js", "springboot");
    }
}
