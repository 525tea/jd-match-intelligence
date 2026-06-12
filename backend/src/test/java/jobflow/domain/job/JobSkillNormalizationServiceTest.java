package jobflow.domain.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.skill.JdSkillNormalizationService;
import jobflow.domain.skill.NormalizedSkillMatch;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JobSkillNormalizationServiceTest {

    @Mock
    private JdSkillNormalizationService jdSkillNormalizationService;

    @Mock
    private JobSkillRepository jobSkillRepository;

    private final JdRequirementSectionExtractor requirementSectionExtractor = new JdRequirementSectionExtractor();

    @Test
    @DisplayName("정규화된 스킬을 저장한다")
    void saveNormalizedSkills() {
        JobSkillNormalizationService service = new JobSkillNormalizationService(
                jdSkillNormalizationService,
                requirementSectionExtractor,
                jobSkillRepository
        );
        Job job = createJob(1L);
        Skill springBoot = Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK);
        Skill kubernetes = Skill.create("Kubernetes", "kubernetes", SkillCategory.INFRA);
        JobSkill springBootJobSkill = JobSkill.create(job, springBoot, RequirementType.REQUIRED);
        JobSkill kubernetesJobSkill = JobSkill.create(job, kubernetes, RequirementType.REQUIRED);

        given(jdSkillNormalizationService.normalize(
                "백엔드 개발자\n\nSpringBoot 기반 서비스 개발\n\nk8s 운영"
        )).willReturn(List.of(
                new NormalizedSkillMatch(
                        springBoot,
                        "SpringBoot",
                        "springboot",
                        BigDecimal.valueOf(0.9500)
                ),
                new NormalizedSkillMatch(
                        kubernetes,
                        "k8s",
                        "k8s",
                        BigDecimal.valueOf(0.9500)
                )
        ));
        given(jobSkillRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .willReturn(List.of(springBootJobSkill, kubernetesJobSkill));

        List<JobSkill> jobSkills = service.saveNormalizedSkills(
                job,
                "백엔드 개발자",
                "SpringBoot 기반 서비스 개발",
                "k8s 운영"
        );

        assertThat(jobSkills)
                .extracting(jobSkill -> jobSkill.getSkill().getName())
                .containsExactly("Spring Boot", "Kubernetes");
        assertThat(jobSkills)
                .extracting(JobSkill::getRequirementType)
                .containsExactly(RequirementType.REQUIRED, RequirementType.REQUIRED);

        verify(jobSkillRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("자격요건과 우대사항 섹션에 따라 requirement type을 저장한다")
    void saveNormalizedSkillsByRequirementSection() {
        JobSkillNormalizationService service = new JobSkillNormalizationService(
                jdSkillNormalizationService,
                requirementSectionExtractor,
                jobSkillRepository
        );
        Job job = createJob(1L);
        Skill java = Skill.create("Java", "java", SkillCategory.LANGUAGE);
        Skill redis = Skill.create("Redis", "redis", SkillCategory.INFRA);

        given(jdSkillNormalizationService.normalize("Java Spring 기반 백엔드 API 개발 경험"))
                .willReturn(List.of(new NormalizedSkillMatch(
                        java,
                        "Java",
                        "java",
                        BigDecimal.ONE
                )));
        given(jdSkillNormalizationService.normalize("Redis 운영 경험"))
                .willReturn(List.of(new NormalizedSkillMatch(
                        redis,
                        "Redis",
                        "redis",
                        BigDecimal.ONE
                )));

        service.saveNormalizedSkills(
                job,
                """
                [자격 요건]
                Java Spring 기반 백엔드 API 개발 경험

                [우대 사항]
                Redis 운영 경험
                """
        );

        ArgumentCaptor<List<JobSkill>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobSkillRepository).saveAll(captor.capture());

        assertThat(captor.getValue())
                .extracting(
                        jobSkill -> jobSkill.getSkill().getName(),
                        JobSkill::getRequirementType
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Java", RequirementType.REQUIRED),
                        org.assertj.core.groups.Tuple.tuple("Redis", RequirementType.PREFERRED)
                );
    }

    @Test
    @DisplayName("같은 스킬이 required와 preferred에 모두 있으면 required를 우선한다")
    void requiredSkillWinsOverPreferredSkill() {
        JobSkillNormalizationService service = new JobSkillNormalizationService(
                jdSkillNormalizationService,
                requirementSectionExtractor,
                jobSkillRepository
        );
        Job job = createJob(1L);
        Skill redis = Skill.create("Redis", "redis", SkillCategory.INFRA);

        given(jdSkillNormalizationService.normalize("Redis 캐시 운영 경험"))
                .willReturn(List.of(new NormalizedSkillMatch(
                        redis,
                        "Redis",
                        "redis",
                        BigDecimal.ONE
                )));
        given(jdSkillNormalizationService.normalize("Redis 튜닝 경험"))
                .willReturn(List.of(new NormalizedSkillMatch(
                        redis,
                        "Redis",
                        "redis",
                        BigDecimal.ONE
                )));

        service.saveNormalizedSkills(
                job,
                """
                [자격 요건]
                Redis 캐시 운영 경험

                [우대 사항]
                Redis 튜닝 경험
                """
        );

        ArgumentCaptor<List<JobSkill>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobSkillRepository).saveAll(captor.capture());

        assertThat(captor.getValue())
                .extracting(
                        jobSkill -> jobSkill.getSkill().getName(),
                        JobSkill::getRequirementType
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Redis", RequirementType.REQUIRED)
                );
    }

    @Test
    @DisplayName("정규화된 스킬이 없으면 빈 목록을 반환한다")
    void saveNormalizedSkillsWithNoMatches() {
        JobSkillNormalizationService service = new JobSkillNormalizationService(
                jdSkillNormalizationService,
                requirementSectionExtractor,
                jobSkillRepository
        );
        Job job = createJob(1L);

        given(jdSkillNormalizationService.normalize("운영 경험"))
                .willReturn(List.of());

        List<JobSkill> jobSkills = service.saveNormalizedSkills(job, "운영 경험");

        assertThat(jobSkills).isEmpty();
    }

    private Job createJob(Long id) {
        Job job = Job.create(
                "JUMPIT",
                "backend-skill-normalization-test",
                "백엔드 개발자",
                "JobFlow",
                "SpringBoot 기반 서비스 개발",
                "https://jumpit.saramin.co.kr/position/backend-skill-normalization-test",
                JobRole.BACKEND,
                "k8s 운영",
                CareerLevel.JUNIOR,
                0,
                3,
                "학력무관",
                EmploymentType.FULL_TIME,
                "STARTUP",
                "IT",
                "KR",
                "Seoul",
                "Gangnam",
                RemoteType.HYBRID,
                4000,
                7000,
                "KRW",
                true,
                1,
                LocalDateTime.of(2026, 6, 4, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        );
        ReflectionTestUtils.setField(job, "id", id);
        return job;
    }
}
