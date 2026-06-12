package jobflow.domain.job.search;

import jobflow.domain.analytics.AnalyticsPeriodType;
import jobflow.domain.analytics.SkillCooccurrence;
import jobflow.domain.analytics.SkillCooccurrenceRepository;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillCategory;
import jobflow.domain.skill.SkillRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobSearchQueryExpansionServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillCooccurrenceRepository skillCooccurrenceRepository;

    @Test
    @DisplayName("검색어에 포함된 스킬의 최신 월 co-occurrence를 support 기준으로 확장한다")
    void expand() {
        LocalDate latestPeriodStart = LocalDate.of(2026, 6, 1);
        Skill redis = skill(1L, "Redis", "redis", SkillCategory.DATABASE);
        Skill springBoot = skill(2L, "Spring Boot", "spring boot", SkillCategory.FRAMEWORK);
        Skill kafka = skill(3L, "Kafka", "kafka", SkillCategory.INFRA);
        Skill mysql = skill(4L, "MySQL", "mysql", SkillCategory.DATABASE);

        JobSearchQueryExpansionService service = new JobSearchQueryExpansionService(
                skillRepository,
                skillCooccurrenceRepository
        );

        given(skillCooccurrenceRepository.findLatestPeriodStartByPeriodType(AnalyticsPeriodType.MONTHLY))
                .willReturn(Optional.of(latestPeriodStart));
        given(skillRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(kafka, mysql, redis, springBoot));
        given(skillCooccurrenceRepository.findSupportedCooccurrences(
                eq(AnalyticsPeriodType.MONTHLY),
                eq(latestPeriodStart),
                eq(redis.getId()),
                eq(3L),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).willReturn(List.of(
                cooccurrence(redis, springBoot, 6, "2.1"),
                cooccurrence(redis, kafka, 4, "1.8"),
                cooccurrence(redis, mysql, 3, "1.4")
        ));

        List<String> expandedSkills = service.expand("Redis 캐시", 2, 3);

        assertThat(expandedSkills).containsExactly("Spring Boot", "Kafka");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(skillCooccurrenceRepository).findSupportedCooccurrences(
                eq(AnalyticsPeriodType.MONTHLY),
                eq(latestPeriodStart),
                eq(redis.getId()),
                eq(3L),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("검색어에 이미 들어간 스킬은 확장 후보에서 제외한다")
    void excludeAlreadyMentionedSkill() {
        LocalDate latestPeriodStart = LocalDate.of(2026, 6, 1);
        Skill redis = skill(1L, "Redis", "redis", SkillCategory.DATABASE);
        Skill springBoot = skill(2L, "Spring Boot", "spring boot", SkillCategory.FRAMEWORK);
        Skill kafka = skill(3L, "Kafka", "kafka", SkillCategory.INFRA);

        JobSearchQueryExpansionService service = new JobSearchQueryExpansionService(
                skillRepository,
                skillCooccurrenceRepository
        );

        given(skillCooccurrenceRepository.findLatestPeriodStartByPeriodType(AnalyticsPeriodType.MONTHLY))
                .willReturn(Optional.of(latestPeriodStart));
        given(skillRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(redis, springBoot));
        given(skillCooccurrenceRepository.findSupportedCooccurrences(
                eq(AnalyticsPeriodType.MONTHLY),
                eq(latestPeriodStart),
                eq(redis.getId()),
                eq(3L),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).willReturn(List.of(
                cooccurrence(redis, springBoot, 6, "2.1"),
                cooccurrence(redis, kafka, 4, "1.8")
        ));

        List<String> expandedSkills = service.expand("Redis Spring Boot", 3, 3);

        assertThat(expandedSkills).containsExactly("Kafka");
    }

    @Test
    @DisplayName("JavaScript 검색어는 Java 스킬로 오인하지 않는다")
    void doNotMatchPartialSkillToken() {
        LocalDate latestPeriodStart = LocalDate.of(2026, 6, 1);
        Skill java = skill(1L, "Java", "java", SkillCategory.LANGUAGE);

        JobSearchQueryExpansionService service = new JobSearchQueryExpansionService(
                skillRepository,
                skillCooccurrenceRepository
        );

        given(skillCooccurrenceRepository.findLatestPeriodStartByPeriodType(AnalyticsPeriodType.MONTHLY))
                .willReturn(Optional.of(latestPeriodStart));
        given(skillRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(java));

        List<String> expandedSkills = service.expand("JavaScript 프론트엔드", 3, 3);

        assertThat(expandedSkills).isEmpty();
        verify(skillCooccurrenceRepository, never()).findSupportedCooccurrences(
                eq(AnalyticsPeriodType.MONTHLY),
                eq(latestPeriodStart),
                eq(java.getId()),
                eq(3L),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        );
    }

    @Test
    @DisplayName("co-occurrence 집계 월이 없으면 확장하지 않는다")
    void noLatestPeriodStart() {
        Skill redis = skill(1L, "Redis", "redis", SkillCategory.DATABASE);
        JobSearchQueryExpansionService service = new JobSearchQueryExpansionService(
                skillRepository,
                skillCooccurrenceRepository
        );

        given(skillCooccurrenceRepository.findLatestPeriodStartByPeriodType(AnalyticsPeriodType.MONTHLY))
                .willReturn(Optional.empty());

        List<String> expandedSkills = service.expand("Redis", 3, 3);

        assertThat(expandedSkills).isEmpty();
        verify(skillRepository, never()).findAllByOrderByNameAsc();
        verify(skillCooccurrenceRepository, never()).findSupportedCooccurrences(
                eq(AnalyticsPeriodType.MONTHLY),
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                eq(redis.getId()),
                eq(3L),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        );
    }

    private Skill skill(Long id, String name, String normalizedName, SkillCategory category) {
        Skill skill = Skill.create(name, normalizedName, category);
        ReflectionTestUtils.setField(skill, "id", id);
        return skill;
    }

    private SkillCooccurrence cooccurrence(
            Skill baseSkill,
            Skill coSkill,
            long cooccurrenceCount,
            String liftScore
    ) {
        return SkillCooccurrence.create(
                AnalyticsPeriodType.MONTHLY,
                LocalDate.of(2026, 6, 1),
                baseSkill,
                coSkill,
                cooccurrenceCount,
                10,
                5,
                new BigDecimal(liftScore)
        );
    }
}
