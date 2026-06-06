package jobflow.domain.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.JdExperienceTagNormalizationService;
import jobflow.domain.skill.JdPhraseTagMapping;
import jobflow.domain.skill.JdPhraseTagMappingRepository;
import jobflow.domain.skill.JdSkillNormalizationService;
import jobflow.domain.skill.NormalizedExperienceTagMatch;
import jobflow.domain.skill.NormalizedSkillMatch;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillAlias;
import jobflow.domain.skill.SkillAliasRepository;
import jobflow.domain.skill.SkillCategory;
import jobflow.domain.skill.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JdParsingRegressionTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillAliasRepository skillAliasRepository;

    @Mock
    private JdPhraseTagMappingRepository jdPhraseTagMappingRepository;

    private JdSkillNormalizationService skillNormalizationService;
    private JdExperienceTagNormalizationService experienceTagNormalizationService;
    private JdJobRoleClassificationService roleClassificationService;

    @BeforeEach
    void setUp() {
        skillNormalizationService = new JdSkillNormalizationService(
                skillRepository,
                skillAliasRepository
        );
        experienceTagNormalizationService = new JdExperienceTagNormalizationService(
                jdPhraseTagMappingRepository
        );
        roleClassificationService = new JdJobRoleClassificationService();
    }

    @Test
    @DisplayName("백엔드 JD에서 skill, experience tag, role을 함께 정규화한다")
    void normalizeBackendJdParsingFlow() {
        Skill java = Skill.create("Java", "java", SkillCategory.LANGUAGE);
        Skill springBoot = Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK);
        ExperienceTagCode highTraffic = createExperienceTagCode("HIGH_TRAFFIC", "대용량 트래픽");
        ExperienceTagCode ciCd = createExperienceTagCode("CI_CD", "CI/CD");

        given(skillRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(java, springBoot));
        given(skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc())
                .willReturn(List.of(
                        SkillAlias.create(
                                springBoot,
                                "SpringBoot",
                                "springboot",
                                BigDecimal.valueOf(0.9500)
                        )
                ));
        given(jdPhraseTagMappingRepository.findByEnabledTrueOrderByNormalizedPhraseAsc())
                .willReturn(List.of(
                        JdPhraseTagMapping.create(
                                "대용량 트래픽",
                                "대용량 트래픽",
                                highTraffic,
                                BigDecimal.valueOf(0.9500)
                        ),
                        JdPhraseTagMapping.create(
                                "CI/CD",
                                "ci/cd",
                                ciCd,
                                BigDecimal.valueOf(0.9500)
                        )
                ));

        String title = "백엔드 개발자";
        String description = "SpringBoot 기반 대용량 트래픽 API 개발과 CI/CD 운영 경험";
        String roleDetail = "Java, JPA, REST API";

        List<NormalizedSkillMatch> skillMatches = skillNormalizationService.normalize(
                title,
                description,
                roleDetail
        );
        List<NormalizedExperienceTagMatch> tagMatches = experienceTagNormalizationService.normalize(
                title,
                description,
                roleDetail
        );
        JobRole role = roleClassificationService.classify(title, description, roleDetail);

        assertThat(skillMatches)
                .extracting(match -> match.skill().getName())
                .containsExactlyInAnyOrder("Java", "Spring Boot");
        assertThat(tagMatches)
                .extracting(match -> match.tagCode().getCode())
                .containsExactlyInAnyOrder("CI_CD", "HIGH_TRAFFIC");
        assertThat(role).isEqualTo(JobRole.BACKEND);
    }

    @Test
    @DisplayName("플랫폼 JD에서 Kubernetes alias, monitoring tag, DEVOPS role을 함께 정규화한다")
    void normalizePlatformJdParsingFlow() {
        Skill kubernetes = Skill.create("Kubernetes", "kubernetes", SkillCategory.INFRA);
        ExperienceTagCode monitoring = createExperienceTagCode("MONITORING", "모니터링");

        given(skillRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(kubernetes));
        given(skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc())
                .willReturn(List.of(
                        SkillAlias.create(
                                kubernetes,
                                "k8s",
                                "k8s",
                                BigDecimal.valueOf(0.9500)
                        )
                ));
        given(jdPhraseTagMappingRepository.findByEnabledTrueOrderByNormalizedPhraseAsc())
                .willReturn(List.of(
                        JdPhraseTagMapping.create(
                                "APM",
                                "apm",
                                monitoring,
                                BigDecimal.valueOf(0.8000)
                        )
                ));

        String title = "플랫폼 엔지니어";
        String description = "k8s 기반 서비스 운영과 APM 장애 추적";
        String roleDetail = "Kubernetes, observability, deployment automation";

        List<NormalizedSkillMatch> skillMatches = skillNormalizationService.normalize(
                title,
                description,
                roleDetail
        );
        List<NormalizedExperienceTagMatch> tagMatches = experienceTagNormalizationService.normalize(
                title,
                description,
                roleDetail
        );
        JobRole role = roleClassificationService.classify(title, description, roleDetail);

        assertThat(skillMatches)
                .extracting(match -> match.skill().getName())
                .containsExactly("Kubernetes");
        assertThat(tagMatches)
                .extracting(match -> match.tagCode().getCode())
                .containsExactly("MONITORING");
        assertThat(role).isEqualTo(JobRole.DEVOPS);
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
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create ExperienceTagCode for test", exception);
        }
    }

    private void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
