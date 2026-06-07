package jobflow.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SkillTrendAggregationBatchConfigTest {

    @Test
    @DisplayName("스킬 트렌드 집계 Batch Job과 Step을 등록한다")
    void registerSkillTrendAggregationJobAndStep(
            @Qualifier(SkillTrendAggregationBatchConfig.SKILL_TREND_AGGREGATION_JOB) Job job,
            @Qualifier(SkillTrendAggregationBatchConfig.SKILL_TREND_AGGREGATION_STEP) Step step
    ) {
        assertThat(job.getName()).isEqualTo(SkillTrendAggregationBatchConfig.SKILL_TREND_AGGREGATION_JOB);
        assertThat(step.getName()).isEqualTo(SkillTrendAggregationBatchConfig.SKILL_TREND_AGGREGATION_STEP);
    }
}
