package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import jobflow.collector.job.CareerLevel;
import jobflow.collector.job.EmploymentType;
import jobflow.collector.job.Job;
import jobflow.collector.job.JobRole;
import jobflow.collector.job.JobSkill;
import jobflow.collector.job.JobSkillRepository;
import jobflow.collector.job.RemoteType;
import jobflow.collector.job.RequirementType;
import jobflow.collector.skill.JdSkillNormalizationService;
import jobflow.collector.skill.NormalizedSkillMatch;
import jobflow.collector.skill.Skill;
import jobflow.collector.skill.SkillCategory;
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
    @DisplayName("기존 job_skills를 지우고 정규화된 스킬을 저장한다")
    void replaceNormalizedSkills() {
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

        List<JobSkill> jobSkills = service.replaceNormalizedSkills(
                job,
                "백엔드 개발자",
                "SpringBoot 기반 서비스 개발",
                "k8s 운영"
        );

        assertThat(jobSkills)
                .extracting(jobSkill -> jobSkill.getSkill().getName())
                .containsExactly("Spring Boot", "Kubernetes");

        verify(jobSkillRepository).deleteByJobId(1L);
        verify(jobSkillRepository).flush();
        verify(jobSkillRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("자격요건과 우대사항 섹션에 따라 requirement type을 저장한다")
    void replaceNormalizedSkillsByRequirementSection() {
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

        service.replaceNormalizedSkills(
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

        service.replaceNormalizedSkills(
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
    @DisplayName("정규화된 스킬이 없어도 기존 job_skills는 비운다")
    void replaceNormalizedSkillsWithNoMatches() {
        JobSkillNormalizationService service = new JobSkillNormalizationService(
                jdSkillNormalizationService,
                requirementSectionExtractor,
                jobSkillRepository
        );
        Job job = createJob(1L);

        given(jdSkillNormalizationService.normalize("백엔드 개발자"))
                .willReturn(List.of());

        List<JobSkill> jobSkills = service.replaceNormalizedSkills(job, "백엔드 개발자");

        assertThat(jobSkills).isEmpty();

        verify(jobSkillRepository).deleteByJobId(1L);
        verify(jobSkillRepository).flush();
    }

    private Job createJob(Long id) {
        Job job = Job.create(
                "ZIGHANG",
                "zighang-skill-normalization-test",
                "백엔드 개발자",
                "JobFlow",
                "SpringBoot 기반 서비스 개발",
                "https://zighang.com/jobs/zighang-skill-normalization-test",
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
