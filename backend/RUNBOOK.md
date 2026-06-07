# JobFlow Backend Runbook

## Skill Trend Aggregation Batch

월별 스킬 트렌드는 Spring Batch job으로 집계한다.

- Job name: `skillTrendAggregationJob`
- Clear step: `skillTrendClearStep`
- Aggregation step: `skillTrendAggregationStep`
- Target table: `skill_trends`
- Target period: monthly, first day of month

### 기본 동작

일반 API 서버 실행 시 Batch job은 자동 실행되지 않는다.

```yaml
spring:
  batch:
    job:
      enabled: false
```

스케줄러는 `SkillTrendAggregationBatchLauncher`를 통해 현재 월 Batch job을 실행한다.

```yaml
jobflow:
  analytics:
    skill-trend:
      fixed-delay: ${SKILL_TREND_AGGREGATION_FIXED_DELAY:3600000}
      initial-delay: ${SKILL_TREND_AGGREGATION_INITIAL_DELAY:60000}
```

### 수동 실행

특정 월 스킬 트렌드를 한 번 집계하려면 runner를 활성화한다.

루트 디렉터리에서 실행:

```bash
./gradlew :backend:bootRun --args='--jobflow.analytics.skill-trend.runner.enabled=true --jobflow.analytics.skill-trend.runner.target-month=2026-06-01'
```

현재 월을 집계하려면 `target-month`를 생략한다.

```bash
./gradlew :backend:bootRun --args='--jobflow.analytics.skill-trend.runner.enabled=true'
```

### 환경 변수 실행

```bash
SKILL_TREND_AGGREGATION_RUNNER_ENABLED=true \
SKILL_TREND_AGGREGATION_TARGET_MONTH=2026-06-01 \
./gradlew :backend:bootRun
```

### 재실행 기준

`targetMonth`는 집계 대상 월이다.

`requestedAt`은 Batch JobInstance를 구분하기 위한 실행 요청 시각이다. 같은 `targetMonth`라도 `requestedAt`이 다르면 별도 Batch 실행으로 기록된다.

재실행 시 같은 월의 기존 `skill_trends`는 먼저 삭제되고, 현재 `job_skills` 기준으로 다시 생성된다.

### 검증 쿼리

```sql
SELECT
    st.period_type,
    st.period_start,
    s.name AS skill_name,
    st.job_count,
    st.required_count,
    st.preferred_count,
    st.trend_score,
    st.computed_at
FROM skill_trends st
JOIN skills s ON s.id = st.skill_id
WHERE st.period_type = 'MONTHLY'
  AND st.period_start = '2026-06-01'
ORDER BY st.trend_score DESC, st.job_count DESC;
```

### 테스트

```bash
./gradlew :backend:test \
  --tests jobflow.domain.analytics.SkillTrendAggregationBatchConfigTest \
  --tests jobflow.domain.analytics.SkillTrendAggregationBatchJobTest \
  --tests jobflow.domain.analytics.SkillTrendAggregationBatchLauncherTest \
  --tests jobflow.domain.analytics.SkillTrendAggregationBatchRunnerTest \
  --tests jobflow.domain.analytics.SkillTrendAggregationSchedulerTest
```
