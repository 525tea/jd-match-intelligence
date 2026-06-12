package jobflow.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobSkill;
import jobflow.domain.job.JobSkillRepository;
import jobflow.domain.job.RemoteType;
import jobflow.domain.job.RequirementType;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillCategory;
import jobflow.domain.skill.SkillRepository;
import jobflow.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, JobSkillIndexRebuildService.class})
class JobSkillIndexRebuildServiceTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private JobSkillRepository jobSkillRepository;

    @Autowired
    private JobSkillIndexRepository jobSkillIndexRepository;

    @Autowired
    private JobSkillIndexRebuildService jobSkillIndexRebuildService;

    @Test
    void openJobSkills_areRebuiltIntoRequirementAwareIndex() {
        String suffix = UUID.randomUUID().toString();

        Skill java = skillRepository.save(Skill.create(
                "Index Java " + suffix,
                "index-java-" + suffix,
                SkillCategory.LANGUAGE
        ));
        Skill redis = skillRepository.save(Skill.create(
                "Index Redis " + suffix,
                "index-redis-" + suffix,
                SkillCategory.INFRA
        ));

        Job job = jobRepository.save(createJob(
                "JUMPIT",
                "index-job-" + suffix,
                "백엔드 개발자",
                "인덱스컴퍼니",
                "필수 요건 Java 우대 사항 Redis",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                "Seoul",
                "Gangnam",
                RemoteType.ONSITE,
                LocalDateTime.now().plusDays(10)
        ));

        jobSkillRepository.save(JobSkill.create(job, java, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(job, redis, RequirementType.PREFERRED));

        JobSkillIndexRebuildResult result = jobSkillIndexRebuildService.rebuild();

        assertThat(result.sourceCount()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(2);
        assertThat(jobSkillIndexRepository.countByRequirementType(RequirementType.REQUIRED)).isGreaterThanOrEqualTo(1);
        assertThat(jobSkillIndexRepository.countByRequirementType(RequirementType.PREFERRED)).isGreaterThanOrEqualTo(1);

        List<JobSkillIndex> indexes = jobSkillIndexRepository.findAll();

        assertThat(indexes)
                .filteredOn(index -> index.getJob().getId().equals(job.getId()))
                .extracting(JobSkillIndex::getRequirementType)
                .containsExactlyInAnyOrder(RequirementType.REQUIRED, RequirementType.PREFERRED);

        assertThat(indexes)
                .filteredOn(index -> index.getJob().getId().equals(job.getId()))
                .allSatisfy(index -> {
                    assertThat(index.getSource()).isEqualTo("JUMPIT");
                    assertThat(index.getRole()).isEqualTo(JobRole.BACKEND);
                    assertThat(index.getCareerLevel()).isEqualTo(CareerLevel.JUNIOR);
                    assertThat(index.getLocationRegion()).isEqualTo("Seoul");
                    assertThat(index.getRemoteType()).isEqualTo(RemoteType.ONSITE);
                    assertThat(index.getComputedAt()).isNotNull();
                });
    }

    @Test
    void rebuild_replacesPreviousIndexRows() {
        String suffix = UUID.randomUUID().toString();

        Skill java = skillRepository.save(Skill.create(
                "Replace Java " + suffix,
                "replace-java-" + suffix,
                SkillCategory.LANGUAGE
        ));
        Skill spring = skillRepository.save(Skill.create(
                "Replace Spring " + suffix,
                "replace-spring-" + suffix,
                SkillCategory.FRAMEWORK
        ));

        Job job = jobRepository.save(createJob(
                "WANTED",
                "replace-job-" + suffix,
                "Spring 백엔드 개발자",
                "교체컴퍼니",
                "필수 요건 Java Spring",
                JobRole.BACKEND,
                CareerLevel.MID,
                "Seoul",
                null,
                RemoteType.REMOTE,
                null
        ));

        jobSkillRepository.save(JobSkill.create(job, java, RequirementType.REQUIRED));

        JobSkillIndexRebuildResult first = jobSkillIndexRebuildService.rebuild();
        assertThat(first.savedCount()).isGreaterThanOrEqualTo(1);

        jobSkillRepository.save(JobSkill.create(job, spring, RequirementType.REQUIRED));

        JobSkillIndexRebuildResult second = jobSkillIndexRebuildService.rebuild();

        assertThat(second.savedCount()).isGreaterThanOrEqualTo(2);
        assertThat(jobSkillIndexRepository.findAll())
                .filteredOn(index -> index.getJob().getId().equals(job.getId()))
                .extracting(index -> index.getSkill().getId())
                .containsExactlyInAnyOrder(java.getId(), spring.getId());
    }

    private Job createJob(
            String source,
            String externalId,
            String title,
            String companyName,
            String description,
            JobRole role,
            CareerLevel careerLevel,
            String locationRegion,
            String locationCity,
            RemoteType remoteType,
            LocalDateTime deadlineAt
    ) {
        return Job.create(
                source,
                externalId,
                title,
                companyName,
                description,
                "https://example.com/jobs/" + externalId,
                role,
                role.name(),
                careerLevel,
                null,
                null,
                null,
                EmploymentType.FULL_TIME,
                null,
                "IT",
                "KR",
                locationRegion,
                locationCity,
                remoteType,
                null,
                null,
                "KRW",
                false,
                null,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                deadlineAt
        );
    }
}
