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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JobSkillNormalizationServiceTest {

    @Mock
    private JdSkillNormalizationService jdSkillNormalizationService;

    @Mock
    private JobSkillRepository jobSkillRepository;

    @Test
    @DisplayName("기존 job_skills를 지우고 정규화된 스킬을 저장한다")
    void replaceNormalizedSkills() {
        JobSkillNormalizationService service = new JobSkillNormalizationService(
                jdSkillNormalizationService,
                jobSkillRepository
        );
        Job job = createJob(1L);
        Skill springBoot = Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK);
        Skill kubernetes = Skill.create("Kubernetes", "kubernetes", SkillCategory.INFRA);
        JobSkill springBootJobSkill = JobSkill.create(job, springBoot, RequirementType.REQUIRED);
        JobSkill kubernetesJobSkill = JobSkill.create(job, kubernetes, RequirementType.REQUIRED);

        given(jdSkillNormalizationService.normalize(
                "백엔드 개발자",
                "SpringBoot 기반 서비스 개발",
                "k8s 운영"
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
    @DisplayName("정규화된 스킬이 없어도 기존 job_skills는 비운다")
    void replaceNormalizedSkillsWithNoMatches() {
        JobSkillNormalizationService service = new JobSkillNormalizationService(
                jdSkillNormalizationService,
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
