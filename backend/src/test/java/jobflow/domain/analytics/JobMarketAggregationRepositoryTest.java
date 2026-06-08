package jobflow.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobExperienceTag;
import jobflow.domain.job.JobExperienceTagRepository;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobSkill;
import jobflow.domain.job.JobSkillRepository;
import jobflow.domain.job.RemoteType;
import jobflow.domain.job.RequirementType;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.ExperienceTagCodeRepository;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillCategory;
import jobflow.domain.skill.SkillRepository;
import jobflow.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class JobMarketAggregationRepositoryTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobSkillRepository jobSkillRepository;

    @Autowired
    private JobExperienceTagRepository jobExperienceTagRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private ExperienceTagCodeRepository experienceTagCodeRepository;

    @Test
    @DisplayName("기간 내 공고의 스킬 동시 등장 쌍을 집계한다")
    void aggregateSkillCooccurrences() {
        Skill springBoot = skillRepository.save(
                Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK)
        );
        Skill jpa = skillRepository.save(
                Skill.create("JPA", "jpa", SkillCategory.FRAMEWORK)
        );
        Skill redis = skillRepository.save(
                Skill.create("Redis", "redis", SkillCategory.DATABASE)
        );
        Job backendJob = jobRepository.save(createJob("cooccurrence-backend"));
        Job platformJob = jobRepository.save(createJob("cooccurrence-platform"));

        jobSkillRepository.save(JobSkill.create(backendJob, springBoot, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(backendJob, jpa, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(platformJob, springBoot, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(platformJob, redis, RequirementType.PREFERRED));
        jobSkillRepository.flush();

        List<JobSkillCooccurrenceAggregate> aggregates = jobSkillRepository.aggregateSkillCooccurrences(
                currentPeriodStart().atStartOfDay(),
                currentPeriodStart().plusMonths(1).atStartOfDay()
        );

        assertThat(aggregates).hasSize(4);
        assertThat(findCooccurrence(aggregates, springBoot, jpa).cooccurrenceCount()).isEqualTo(1L);
        assertThat(findCooccurrence(aggregates, jpa, springBoot).cooccurrenceCount()).isEqualTo(1L);
        assertThat(findCooccurrence(aggregates, springBoot, redis).cooccurrenceCount()).isEqualTo(1L);
        assertThat(findCooccurrence(aggregates, redis, springBoot).cooccurrenceCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("기간 내 공고의 경험 태그별 스킬 수요를 집계한다")
    void aggregateSkillExperienceMarkets() {
        Skill springBoot = skillRepository.save(
                Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK)
        );
        Skill redis = skillRepository.save(
                Skill.create("Redis", "redis", SkillCategory.DATABASE)
        );
        ExperienceTagCode traffic = experienceTagCodeRepository.save(
                ExperienceTagCodeTestFactory.create("TRAFFIC_TEST", "대용량 트래픽", "대용량 트래픽 처리 경험")
        );
        Job backendJob = jobRepository.save(createJob("experience-backend"));
        Job platformJob = jobRepository.save(createJob("experience-platform"));

        jobSkillRepository.save(JobSkill.create(backendJob, springBoot, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(platformJob, springBoot, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(platformJob, redis, RequirementType.PREFERRED));
        jobExperienceTagRepository.save(JobExperienceTag.create(backendJob, traffic, "대용량 트래픽"));
        jobExperienceTagRepository.save(JobExperienceTag.create(platformJob, traffic, "대용량 트래픽"));
        jobSkillRepository.flush();
        jobExperienceTagRepository.flush();

        List<JobSkillExperienceMarketAggregate> skillTagAggregates =
                jobExperienceTagRepository.aggregateSkillExperienceMarkets(
                        currentPeriodStart().atStartOfDay(),
                        currentPeriodStart().plusMonths(1).atStartOfDay()
                );
        List<ExperienceTagMarketAggregate> tagAggregates =
                jobExperienceTagRepository.aggregateExperienceTagMarkets(
                        currentPeriodStart().atStartOfDay(),
                        currentPeriodStart().plusMonths(1).atStartOfDay()
                );

        assertThat(skillTagAggregates).hasSize(2);
        assertThat(findSkillExperience(skillTagAggregates, springBoot, traffic).jobCount()).isEqualTo(2L);
        assertThat(findSkillExperience(skillTagAggregates, redis, traffic).jobCount()).isEqualTo(1L);

        assertThat(tagAggregates).hasSize(1);
        assertThat(tagAggregates.get(0).tagCode().getCode()).isEqualTo("TRAFFIC_TEST");
        assertThat(tagAggregates.get(0).jobCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("기간 내 공고를 시장 통계 차원으로 집계한다")
    void aggregateJobMarketStats() {
        Job juniorBackendJob = jobRepository.save(createJob(
                "market-stats-junior-backend",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                "Seoul",
                RemoteType.HYBRID
        ));
        Job anotherJuniorBackendJob = jobRepository.save(createJob(
                "market-stats-junior-backend-2",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                "Seoul",
                RemoteType.HYBRID
        ));
        Job midBackendJob = jobRepository.save(createJob(
                "market-stats-mid-backend",
                JobRole.BACKEND,
                CareerLevel.MID,
                "Gyeonggi",
                RemoteType.REMOTE
        ));
        jobRepository.saveAll(List.of(juniorBackendJob, anotherJuniorBackendJob, midBackendJob));
        jobRepository.flush();

        List<JobMarketStatsAggregate> aggregates = jobRepository.aggregateJobMarketStats(
                currentPeriodStart().atStartOfDay(),
                currentPeriodStart().plusMonths(1).atStartOfDay()
        );

        JobMarketStatsAggregate juniorBackendAggregate = findJobMarketStats(
                aggregates,
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                "Seoul",
                RemoteType.HYBRID
        );
        JobMarketStatsAggregate midBackendAggregate = findJobMarketStats(
                aggregates,
                JobRole.BACKEND,
                CareerLevel.MID,
                "Gyeonggi",
                RemoteType.REMOTE
        );

        assertThat(juniorBackendAggregate.jobCount()).isEqualTo(2L);
        assertThat(juniorBackendAggregate.openJobCount()).isEqualTo(2L);
        assertThat(juniorBackendAggregate.closedJobCount()).isEqualTo(0L);
        assertThat(juniorBackendAggregate.expiredJobCount()).isEqualTo(0L);
        assertThat(juniorBackendAggregate.avgMinExperienceYears()).isEqualByComparingTo(0.0);
        assertThat(juniorBackendAggregate.avgMaxExperienceYears()).isEqualByComparingTo(3.0);

        assertThat(midBackendAggregate.jobCount()).isEqualTo(1L);
        assertThat(midBackendAggregate.openJobCount()).isEqualTo(1L);
    }

    private JobSkillCooccurrenceAggregate findCooccurrence(
            List<JobSkillCooccurrenceAggregate> aggregates,
            Skill baseSkill,
            Skill coSkill
    ) {
        return aggregates.stream()
                .filter(aggregate -> aggregate.baseSkill().getId().equals(baseSkill.getId()))
                .filter(aggregate -> aggregate.coSkill().getId().equals(coSkill.getId()))
                .findFirst()
                .orElseThrow();
    }

    private JobSkillExperienceMarketAggregate findSkillExperience(
            List<JobSkillExperienceMarketAggregate> aggregates,
            Skill skill,
            ExperienceTagCode tagCode
    ) {
        return aggregates.stream()
                .filter(aggregate -> aggregate.skill().getId().equals(skill.getId()))
                .filter(aggregate -> aggregate.tagCode().getCode().equals(tagCode.getCode()))
                .findFirst()
                .orElseThrow();
    }

    private JobMarketStatsAggregate findJobMarketStats(
            List<JobMarketStatsAggregate> aggregates,
            JobRole role,
            CareerLevel careerLevel,
            String locationRegion,
            RemoteType remoteType
    ) {
        return aggregates.stream()
                .filter(aggregate -> aggregate.role() == role)
                .filter(aggregate -> aggregate.careerLevel() == careerLevel)
                .filter(aggregate -> aggregate.locationRegion().equals(locationRegion))
                .filter(aggregate -> aggregate.remoteType() == remoteType)
                .findFirst()
                .orElseThrow();
    }

    private LocalDate currentPeriodStart() {
        return LocalDate.now().withDayOfMonth(1);
    }

    private Job createJob(String externalId) {
        return Job.create(
                "ANALYTICS_MARKET_TEST",
                externalId,
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 기반 백엔드 API 개발",
                "https://example.com/jobs/" + externalId,
                JobRole.BACKEND,
                "Java Spring Boot JPA",
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

    private Job createJob(
            String externalId,
            JobRole role,
            CareerLevel careerLevel,
            String locationRegion,
            RemoteType remoteType
    ) {
        return Job.create(
                "ANALYTICS_MARKET_TEST",
                externalId,
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 기반 백엔드 API 개발",
                "https://example.com/jobs/" + externalId,
                role,
                "Java Spring Boot JPA",
                careerLevel,
                0,
                3,
                null,
                EmploymentType.FULL_TIME,
                null,
                "IT",
                "KR",
                locationRegion,
                "Gangnam",
                remoteType,
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
