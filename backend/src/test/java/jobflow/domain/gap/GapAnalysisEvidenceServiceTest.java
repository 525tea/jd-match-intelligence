package jobflow.domain.gap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jobflow.domain.analytics.AnalyticsPeriodType;
import jobflow.domain.analytics.SkillCooccurrence;
import jobflow.domain.analytics.SkillCooccurrenceRepository;
import jobflow.domain.analytics.SkillExperienceMarket;
import jobflow.domain.analytics.SkillExperienceMarketRepository;
import jobflow.domain.analytics.SkillTrend;
import jobflow.domain.analytics.SkillTrendRepository;
import jobflow.domain.analytics.dto.JobSkillMatchResponse;
import jobflow.domain.gap.dto.GapJobMatchEvidenceResponse;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class GapAnalysisEvidenceServiceTest {

    private final SkillTrendRepository skillTrendRepository =
            mock(SkillTrendRepository.class);

    private final SkillCooccurrenceRepository skillCooccurrenceRepository =
            mock(SkillCooccurrenceRepository.class);

    private final SkillExperienceMarketRepository skillExperienceMarketRepository =
            mock(SkillExperienceMarketRepository.class);

    private final GapAnalysisEvidenceService gapAnalysisEvidenceService =
            new GapAnalysisEvidenceService(
                    skillTrendRepository,
                    skillCooccurrenceRepository,
                    skillExperienceMarketRepository
            );

    @Test
    @DisplayName("missing skill 기반으로 market evidence와 learning connection을 생성한다")
    void buildEvidence() {
        LocalDate periodStart = LocalDate.of(2026, 6, 1);
        Skill kubernetes = skill("Kubernetes", "kubernetes", SkillCategory.INFRA);
        Skill docker = skill("Docker", "docker", SkillCategory.INFRA);
        ExperienceTagCode cloudInfra = experienceTagCode("CLOUD_INFRA", "클라우드/인프라", "클라우드 인프라 경험");

        SkillTrend kubernetesTrend = skillTrend(kubernetes, 43);
        SkillCooccurrence cooccurrence = skillCooccurrence(kubernetes, docker, 12, 43, 61, "2.5000");
        SkillExperienceMarket market = skillExperienceMarket(kubernetes, cloudInfra, 20, 43, 120, "1.7000");

        given(skillTrendRepository.findLatestPeriodStartByPeriodType(AnalyticsPeriodType.MONTHLY))
                .willReturn(Optional.of(periodStart));
        given(skillTrendRepository.findByPeriodTypeAndPeriodStartAndSkillNameIn(
                eq(AnalyticsPeriodType.MONTHLY),
                eq(periodStart),
                anyCollection()
        )).willReturn(List.of(kubernetesTrend));

        given(skillCooccurrenceRepository.findLatestPeriodStartByPeriodType(AnalyticsPeriodType.MONTHLY))
                .willReturn(Optional.of(periodStart));
        given(skillCooccurrenceRepository.findSupportedCooccurrencesByBaseSkillNameIn(
                eq(AnalyticsPeriodType.MONTHLY),
                eq(periodStart),
                anyCollection(),
                eq(3L),
                any(Pageable.class)
        )).willReturn(List.of(cooccurrence));

        given(skillExperienceMarketRepository.findLatestPeriodStartByPeriodType(AnalyticsPeriodType.MONTHLY))
                .willReturn(Optional.of(periodStart));
        given(skillExperienceMarketRepository.findSupportedMarketsBySkillNameIn(
                eq(AnalyticsPeriodType.MONTHLY),
                eq(periodStart),
                anyCollection(),
                eq(3L),
                any(Pageable.class)
        )).willReturn(List.of(market));

        GapJobMatchEvidenceResponse evidence = gapAnalysisEvidenceService.buildEvidence(
                jobSkillMatchResponse(List.of("Kubernetes"), List.of("Kubernetes"))
        );

        assertThat(evidence.addedJobs()).isEqualTo(43);
        assertThat(evidence.cooccurrences()).hasSize(1);
        assertThat(evidence.cooccurrences().get(0).baseSkillName()).isEqualTo("Kubernetes");
        assertThat(evidence.cooccurrences().get(0).relatedSkillName()).isEqualTo("Docker");
        assertThat(evidence.cooccurrences().get(0).cooccurrenceCount()).isEqualTo(12);
        assertThat(evidence.relatedTags()).hasSize(1);
        assertThat(evidence.relatedTags().get(0).skillName()).isEqualTo("Kubernetes");
        assertThat(evidence.relatedTags().get(0).tagCode()).isEqualTo("CLOUD_INFRA");
        assertThat(evidence.learningConnections()).hasSize(1);
        assertThat(evidence.learningConnections().get(0).missingSkillName()).isEqualTo("Kubernetes");
        assertThat(evidence.learningConnections().get(0).reason())
                .contains("Kubernetes")
                .contains("Docker")
                .contains("클라우드/인프라");
    }

    @Test
    @DisplayName("missing skill이 없으면 집계 테이블을 조회하지 않고 빈 evidence를 반환한다")
    void buildEvidenceWithoutMissingSkills() {
        GapJobMatchEvidenceResponse evidence = gapAnalysisEvidenceService.buildEvidence(
                jobSkillMatchResponse(List.of(), List.of())
        );

        assertThat(evidence.addedJobs()).isZero();
        assertThat(evidence.cooccurrences()).isEmpty();
        assertThat(evidence.relatedTags()).isEmpty();
        assertThat(evidence.learningConnections()).isEmpty();

        verifyNoInteractions(
                skillTrendRepository,
                skillCooccurrenceRepository,
                skillExperienceMarketRepository
        );
    }

    @Test
    @DisplayName("집계 period가 없으면 missing skill마다 기본 learning connection만 생성한다")
    void buildEvidenceWithoutAnalyticsPeriod() {
        given(skillTrendRepository.findLatestPeriodStartByPeriodType(AnalyticsPeriodType.MONTHLY))
                .willReturn(Optional.empty());
        given(skillCooccurrenceRepository.findLatestPeriodStartByPeriodType(AnalyticsPeriodType.MONTHLY))
                .willReturn(Optional.empty());
        given(skillExperienceMarketRepository.findLatestPeriodStartByPeriodType(AnalyticsPeriodType.MONTHLY))
                .willReturn(Optional.empty());

        GapJobMatchEvidenceResponse evidence = gapAnalysisEvidenceService.buildEvidence(
                jobSkillMatchResponse(List.of("Kubernetes"), List.of("Kafka"))
        );

        assertThat(evidence.addedJobs()).isZero();
        assertThat(evidence.cooccurrences()).isEmpty();
        assertThat(evidence.relatedTags()).isEmpty();
        assertThat(evidence.learningConnections()).hasSize(2);
        assertThat(evidence.learningConnections())
                .extracting("missingSkillName")
                .containsExactly("Kubernetes", "Kafka");
    }

    private JobSkillMatchResponse jobSkillMatchResponse(
            List<String> missingRequiredSkills,
            List<String> missingPreferredSkills
    ) {
        return new JobSkillMatchResponse(
                100L,
                "백엔드 개발자",
                "Example Company",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                2,
                1,
                missingRequiredSkills.size(),
                BigDecimal.valueOf(50.00),
                2,
                1,
                missingPreferredSkills.size(),
                BigDecimal.valueOf(50.00),
                BigDecimal.valueOf(56.33),
                List.of("Java"),
                missingRequiredSkills,
                List.of("Docker"),
                missingPreferredSkills
        );
    }

    private Skill skill(String name, String normalizedName, SkillCategory category) {
        return Skill.create(name, normalizedName, category);
    }

    private SkillTrend skillTrend(Skill skill, long jobCount) {
        return SkillTrend.create(
                AnalyticsPeriodType.MONTHLY,
                LocalDate.of(2026, 6, 1),
                skill,
                jobCount,
                jobCount,
                0,
                BigDecimal.valueOf(jobCount * 2)
        );
    }

    private SkillCooccurrence skillCooccurrence(
            Skill baseSkill,
            Skill coSkill,
            long cooccurrenceCount,
            long baseSkillJobCount,
            long coSkillJobCount,
            String liftScore
    ) {
        return SkillCooccurrence.create(
                AnalyticsPeriodType.MONTHLY,
                LocalDate.of(2026, 6, 1),
                baseSkill,
                coSkill,
                cooccurrenceCount,
                baseSkillJobCount,
                coSkillJobCount,
                new BigDecimal(liftScore)
        );
    }

    private SkillExperienceMarket skillExperienceMarket(
            Skill skill,
            ExperienceTagCode tagCode,
            long jobCount,
            long skillJobCount,
            long tagJobCount,
            String liftScore
    ) {
        return SkillExperienceMarket.create(
                AnalyticsPeriodType.MONTHLY,
                LocalDate.of(2026, 6, 1),
                skill,
                tagCode,
                jobCount,
                skillJobCount,
                tagJobCount,
                new BigDecimal(liftScore)
        );
    }

    private ExperienceTagCode experienceTagCode(String code, String name, String description) {
        ExperienceTagCode tagCode = mock(ExperienceTagCode.class);
        given(tagCode.getCode()).willReturn(code);
        given(tagCode.getName()).willReturn(name);
        given(tagCode.getDescription()).willReturn(description);
        return tagCode;
    }
}
