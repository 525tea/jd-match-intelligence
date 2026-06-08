# JobFlow Backend Runbook

## Analytics Aggregation Batch

월별 Analytics 집계는 Spring Batch job으로 실행한다.

- Job name: `skillTrendAggregationJob`
- Clear step: `skillTrendClearStep`
- Skill trend aggregation step: `skillTrendAggregationStep`
- Skill market aggregation step: `skillMarketAggregationStep`
- Job market stats aggregation step: `jobMarketStatsAggregationStep`
- Target tables:
  - `skill_trends`
  - `skill_cooccurrence`
  - `skill_experience_market`
  - `job_market_stats`
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

이 job은 아래 순서로 월별 Analytics table을 재생성한다.

1. 기존 `skill_trends` 삭제
2. `job_skills` 기반 `skill_trends` 생성
3. `job_skills`, `job_experience_tags` 기반 `skill_cooccurrence`, `skill_experience_market` 재생성
4. `jobs` 기반 `job_market_stats` 재생성

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

로컬 MySQL에 붙여 실행할 때는 `local` profile을 사용한다. 이미 API 서버가 8080 포트를 쓰고 있다면 `--server.port=0`으로 임의 포트를 사용한다.

```bash
cd /Users/iyejin/dev/jobflow/backend

SKILL_TREND_AGGREGATION_RUNNER_ENABLED=true \
SKILL_TREND_AGGREGATION_SCHEDULER_ENABLED=false \
./gradlew bootRun --args='--spring.profiles.active=local --server.port=0 --jobflow.analytics.skill-trend.runner.target-month=2026-06-01'
```

현재 월을 집계하려면 `target-month`를 생략한다.

```bash
cd /Users/iyejin/dev/jobflow/backend

SKILL_TREND_AGGREGATION_RUNNER_ENABLED=true \
SKILL_TREND_AGGREGATION_SCHEDULER_ENABLED=false \
./gradlew bootRun --args='--spring.profiles.active=local --server.port=0'
```

실행 로그에서 아래 메시지를 확인한다.

```text
Skill trend aggregation batch runner completed
status=COMPLETED
```

`bootRun`은 웹 애플리케이션으로 계속 떠 있을 수 있으므로 완료 로그 확인 후 `Ctrl+C`로 종료한다.

### 재실행 기준

`targetMonth`는 집계 대상 월이다.

`requestedAt`은 Batch JobInstance를 구분하기 위한 실행 요청 시각이다. 같은 `targetMonth`라도 `requestedAt`이 다르면 별도 Batch 실행으로 기록된다.

재실행 시 같은 월의 기존 `skill_trends`, `skill_cooccurrence`, `skill_experience_market`, `job_market_stats`는 먼저 삭제되고, 현재 transaction DB 기준으로 다시 생성된다.

### 검증 쿼리

아래 SQL 파일은 월별 Analytics 결과를 한 번에 확인한다.

```bash
cd /Users/iyejin/dev/jobflow

docker compose exec -T mysql mysql -u jobflow -pjobflow jobflow < performance/sql/analytics-market-aggregation-check.sql
```

확인 대상:

- `skill_trends`
- `skill_cooccurrence`
- `skill_experience_market`
- `job_market_stats`

### Trend API 스모크

API 서버가 떠 있는 상태에서 Trend API 응답을 확인한다.

```bash
cd /Users/iyejin/dev/jobflow

BASE_URL=http://localhost:8080 \
MONTH=2026-06-01 \
LIMIT=10 \
bash performance/analytics/analytics-trend-api-smoke.sh
```

특정 스킬의 동시 등장/경험 태그 시장 API까지 확인하려면 `SKILL_ID`를 지정한다.

```bash
BASE_URL=http://localhost:8080 \
MONTH=2026-06-01 \
LIMIT=10 \
SKILL_ID=7 \
bash performance/analytics/analytics-trend-api-smoke.sh
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
  --tests jobflow.domain.analytics.JobMarketAggregationRepositoryTest \
  --tests jobflow.domain.analytics.AnalyticsTrendServiceTest \
  --tests jobflow.domain.analytics.AnalyticsTrendControllerTest
```
