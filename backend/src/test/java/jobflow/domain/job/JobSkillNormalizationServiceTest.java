package jobflow.domain.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobSkillNormalizationServiceTest {

    @Mock
    private JdSkillNormalizationService jdSkillNormalizationService;

    @Mock
    private JobSkillRepository jobSkillRepository;

    @Test
    @DisplayName("정규화된 스킬을 job_skills로 저장한다")
    void saveNormalizedSkills() {
        JobSkillNormalizationService service = new JobSkillNormalizationService(
                jdSkillNormalizationService,
                jobSkillRepository
        );
        Job job = createJob();
        Skill springBoot = Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK);
        Skill kubernetes = Skill.create("Kubernetes", "kubernetes", SkillCategory.INFRA);
        JobSkill springBootJobSkill = JobSkill.create(job, springBoot, RequirementType.REQUIRED);
        JobSkill kubernetesJobSkill = JobSkill.create(job, kubernetes, RequirementType.REQUIRED);

        given(jdSkillNormalizationService.normalize(
                "SpringBoot 백엔드 개발자",
                "k8s 운영 경험"
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
                "SpringBoot 백엔드 개발자",
                "k8s 운영 경험"
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
    @DisplayName("정규화된 스킬이 없으면 job_skills를 저장하지 않는다")
    void saveNormalizedSkillsWithNoMatches() {
        JobSkillNormalizationService service = new JobSkillNormalizationService(
                jdSkillNormalizationService,
                jobSkillRepository
        );
        Job job = createJob();

        given(jdSkillNormalizationService.normalize("운영 경험"))
                .willReturn(List.of());

        List<JobSkill> jobSkills = service.saveNormalizedSkills(job, "운영 경험");

        assertThat(jobSkills).isEmpty();

        verify(jobSkillRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    private Job createJob() {
        return Job.create(
                "MANUAL",
                "job-skill-normalization-test",
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 기반 백엔드 개발",
                "https://example.com/jobs/job-skill-normalization-test",
                JobRole.BACKEND,
                "Java Spring Boot",
                CareerLevel.JUNIOR,
                0,
                3,
                null,
                EmploymentType.FULL_TIME,
                null,
                "IT",
                "KR",
                "Seoul",
                "Gangnam",
                RemoteType.HYBRID,
                null,
                null,
                "KRW",
                false,
                null,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        );
    }
}
