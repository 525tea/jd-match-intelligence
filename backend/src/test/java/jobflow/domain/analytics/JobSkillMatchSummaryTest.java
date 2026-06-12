package jobflow.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobSkillMatchSummaryTest {

    @Test
    @DisplayName("집계 count가 null이면 0으로 정규화한다")
    void normalizeNullCountsToZero() {
        JobSkillMatchSummary summary = new JobSkillMatchSummary(
                1L,
                "백엔드 개발자",
                "JobFlow",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                null,
                null,
                null,
                null
        );

        assertThat(summary.requiredSkillCount()).isZero();
        assertThat(summary.matchedRequiredSkillCount()).isZero();
        assertThat(summary.missingRequiredSkillCount()).isZero();
        assertThat(summary.requiredMatchRate()).isNull();
        assertThat(summary.preferredSkillCount()).isZero();
        assertThat(summary.matchedPreferredSkillCount()).isZero();
        assertThat(summary.missingPreferredSkillCount()).isZero();
        assertThat(summary.preferredMatchRate()).isNull();
        assertThat(summary.matchScore()).isZero();
    }

    @Test
    @DisplayName("필수 또는 우대 스킬 bucket이 비어 있으면 match rate는 null이다")
    void returnNullMatchRateWhenBucketIsEmpty() {
        JobSkillMatchSummary preferredOnlySummary = new JobSkillMatchSummary(
                1L,
                "Redis 우대 백엔드 개발자",
                "JobFlow",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                0L,
                0L,
                1L,
                1L
        );
        JobSkillMatchSummary requiredOnlySummary = new JobSkillMatchSummary(
                2L,
                "Java 필수 백엔드 개발자",
                "JobFlow",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                1L,
                1L,
                0L,
                0L
        );

        assertThat(preferredOnlySummary.requiredMatchRate()).isNull();
        assertThat(preferredOnlySummary.preferredMatchRate()).isEqualTo(1.0);
        assertThat(requiredOnlySummary.requiredMatchRate()).isEqualTo(1.0);
        assertThat(requiredOnlySummary.preferredMatchRate()).isNull();
    }
}
