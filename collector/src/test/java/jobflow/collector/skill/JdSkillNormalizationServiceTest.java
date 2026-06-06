package jobflow.collector.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JdSkillNormalizationServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillAliasRepository skillAliasRepository;

    @Test
    @DisplayName("collector JD 텍스트에서 기준 스킬 이름을 정규화한다")
    void normalizeCanonicalSkillNames() {
        JdSkillNormalizationService service = new JdSkillNormalizationService(
                skillRepository,
                skillAliasRepository
        );
        Skill javaScript = Skill.create("JavaScript", "javascript", SkillCategory.LANGUAGE);
        Skill docker = Skill.create("Docker", "docker", SkillCategory.INFRA);

        given(skillRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(docker, javaScript));
        given(skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc())
                .willReturn(List.of());

        List<NormalizedSkillMatch> matches = service.normalize(
                "JavaScript 기반 API와 Docker 배포 경험"
        );

        assertThat(matches)
                .extracting(match -> match.skill().getName())
                .containsExactly("Docker", "JavaScript");
    }

    @Test
    @DisplayName("collector JD 텍스트에서 alias 표현을 기준 스킬로 정규화한다")
    void normalizeSkillAliases() {
        JdSkillNormalizationService service = new JdSkillNormalizationService(
                skillRepository,
                skillAliasRepository
        );
        Skill springBoot = Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK);
        Skill kubernetes = Skill.create("Kubernetes", "kubernetes", SkillCategory.INFRA);

        given(skillRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(springBoot, kubernetes));
        given(skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc())
                .willReturn(List.of(
                        SkillAlias.create(
                                springBoot,
                                "SpringBoot",
                                "springboot",
                                BigDecimal.valueOf(0.9500)
                        ),
                        SkillAlias.create(
                                kubernetes,
                                "k8s",
                                "k8s",
                                BigDecimal.valueOf(0.9500)
                        )
                ));

        List<NormalizedSkillMatch> matches = service.normalize(
                "SpringBoot 기반 백엔드와 k8s 운영 경험"
        );

        assertThat(matches)
                .extracting(match -> match.skill().getName())
                .containsExactly("Kubernetes", "Spring Boot");
    }

    @Test
    @DisplayName("collector JD 텍스트가 비어 있으면 repository를 조회하지 않는다")
    void normalizeBlankText() {
        JdSkillNormalizationService service = new JdSkillNormalizationService(
                skillRepository,
                skillAliasRepository
        );

        List<NormalizedSkillMatch> matches = service.normalize(" ", null);

        assertThat(matches).isEmpty();

        verify(skillRepository, never()).findAllByOrderByNameAsc();
        verify(skillAliasRepository, never()).findByEnabledTrueOrderByNormalizedAliasAsc();
    }
}
