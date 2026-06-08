# JobFlow Backend Runbook

## Analytics Aggregation Batch

월별 Analytics 집계는 Spring Batch job으로 실행한다.

- Job name: `skillTrendAggregationJob`
- Clear step: `skillTrendClearStep`
- Skill trend aggregation step: `skillTrendAggregationStep`
- Skill market aggregation step: `skillMarketAggregationStep`
- Target tables:
    - `skill_trends`
    - `skill_cooccurrence`
    - `skill_experience_market`
- Target period: monthly, first day of month

### 기본 동작

Spring Boot의 Batch job 자동 실행은 비활성화되어 있다. 스케줄 실행은 `jobflow.analytics.skill-trend.scheduler.enabled`로 별도 제어한다.

```yaml
spring:
  batch:
    job:
      enabled: false
```

스케줄러는 `SkillTrendAggregationBatchLauncher`를 통해 현재 월 Batch job을 실행한다.
이 job은 스킬 트렌드 집계 이후 스킬 동시 등장과 스킬-경험 태그 시장 집계를 이어서 수행한다.

```yaml
jobflow:
  analytics:
    skill-trend:
      scheduler:
        enabled: ${SKILL_TREND_AGGREGATION_SCHEDULER_ENABLED:true}
      fixed-delay: ${SKILL_TREND_AGGREGATION_FIXED_DELAY:3600000}
      initial-delay: ${SKILL_TREND_AGGREGATION_INITIAL_DELAY:60000}
```

### 수동 실행

특정 월 Analytics 집계를 한 번 실행하려면 runner를 활성화한다.
수동 실행만 확인하고 scheduler를 끄고 싶으면 `SKILL_TREND_AGGREGATION_SCHEDULER_ENABLED=false`를 함께 지정한다.

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
SKILL_TREND_AGGREGATION_SCHEDULER_ENABLED=false \
SKILL_TREND_AGGREGATION_RUNNER_ENABLED=true \
SKILL_TREND_AGGREGATION_TARGET_MONTH=2026-06-01 \
./gradlew :backend:bootRun
```

### 재실행 기준

`targetMonth`는 집계 대상 월이다.

`requestedAt`은 Batch JobInstance를 구분하기 위한 실행 요청 시각이다. 같은 `targetMonth`라도 `requestedAt`이 다르면 별도 Batch 실행으로 기록된다.

재실행 시 같은 월의 기존 `skill_trends`, `skill_cooccurrence`, `skill_experience_market`는 먼저 삭제되고, 현재 `job_skills`, `job_experience_tags` 기준으로 다시 생성된다.

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

스킬 동시 등장과 스킬-경험 태그 시장 집계는 아래 SQL 파일로 함께 확인한다.

```bash
docker compose exec -T mysql mysql -u jobflow -pjobflow jobflow < performance/sql/analytics-market-aggregation-check.sql
```

### 테스트

```bash
./gradlew :backend:test \
  --tests jobflow.domain.analytics.SkillTrendAggregationBatchConfigTest \
  --tests jobflow.domain.analytics.SkillTrendAggregationBatchJobTest \
  --tests jobflow.domain.analytics.SkillTrendAggregationBatchLauncherTest \
  --tests jobflow.domain.analytics.SkillTrendAggregationBatchRunnerTest \
  --tests jobflow.domain.analytics.SkillTrendAggregationSchedulerTest \
  --tests jobflow.domain.analytics.SkillMarketAggregationServiceTest \
  --tests jobflow.domain.analytics.JobMarketAggregationRepositoryTest
```
