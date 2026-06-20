# MySQL Testcontainers Migration Smoke Report

## 목적

기존 backend 테스트는 `application-test.yml` 기준으로 H2 in-memory DB를 사용했다.
H2는 빠른 단위/슬라이스 테스트에는 적합하지만, JobFlow가 실제로 사용하는 MySQL 전용 기능을 충분히 검증하지 못한다.

이번 smoke는 Testcontainers 기반 MySQL 8.4 임시 DB에서 다음 항목을 검증한다.

- Flyway migration이 실제 MySQL에서 끝까지 적용되는지
- Spring Batch metadata table이 MySQL 환경에 존재하는지
- `jobs` FULLTEXT index가 실제 MySQL에서 생성되는지
- `MATCH ... AGAINST` 기반 FULLTEXT 검색이 동작하는지
- H2와 MySQL dialect 차이로 놓칠 수 있는 schema risk를 CI에서 조기에 잡을 수 있는지

## 검증 환경

| 항목 | 값 |
|---|---|
| Module | `backend` |
| Test DB | Testcontainers `mysql:8.4` |
| Database name | `jobflow_test` |
| Migration | Flyway `V1` ~ `V17` |
| Test class | `jobflow.integration.MySqlSchemaMigrationIntegrationTest` |
| 실행일 | 2026-06-21 |

## 실행 명령

```bash
./gradlew :backend:test --tests jobflow.integration.MySqlSchemaMigrationIntegrationTest
```

## 결과

```text
BUILD SUCCESSFUL in 54s
6 actionable tasks: 6 executed
```

## 확인한 항목

### Flyway Migration

`flyway_schema_history`에서 실패 migration이 없는지 확인했다.

- failed migration count: `0`
- Spring Batch metadata migration `V17`: applied

### Core Table

다음 핵심 table이 MySQL container 안에 생성되는지 확인했다.

- `users`
- `jobs`
- `skills`
- `job_skills`
- `job_experience_tags`
- `outbox_events`

### Spring Batch Metadata Table

MySQL staging/production 환경에서는 `spring.batch.jdbc.initialize-schema: embedded`가 Batch table을 자동 생성하지 않는다.
따라서 Flyway migration으로 Batch metadata table을 명시 생성하고, Testcontainers에서 존재 여부를 검증했다.

검증 table:

- `BATCH_JOB_INSTANCE`
- `BATCH_JOB_EXECUTION`
- `BATCH_JOB_EXECUTION_PARAMS`
- `BATCH_STEP_EXECUTION`
- `BATCH_STEP_EXECUTION_CONTEXT`
- `BATCH_JOB_EXECUTION_CONTEXT`
- `BATCH_STEP_EXECUTION_SEQ`
- `BATCH_JOB_EXECUTION_SEQ`
- `BATCH_JOB_SEQ`

### MySQL FULLTEXT

`jobs` table의 `ft_jobs_search` FULLTEXT index가 실제 MySQL에서 생성되는지 확인했다.

검증 대상 column:

- `title`
- `company_name`
- `description`
- `role_detail`
- `industry`
- `location_region`
- `location_city`

테스트 fixture를 insert한 뒤 다음 검색이 1건을 반환하는지 확인했다.

```sql
MATCH (
    title,
    company_name,
    description,
    role_detail,
    industry,
    location_region,
    location_city
) AGAINST ('+spring +backend' IN BOOLEAN MODE)
```

## 닫은 리스크

| 리스크 | 결과 |
|---|---|
| H2에서는 통과하지만 MySQL migration에서 실패하는 문제 | Testcontainers MySQL로 보강 |
| staging 첫 Batch 실행 시 `BATCH_*` table 누락 | `V17` Flyway migration으로 보강 |
| MySQL FULLTEXT index 누락/문법 차이 | 실제 MySQL에서 index와 검색 동작 검증 |
| 로컬 Docker Compose DB 상태에 의존하는 테스트 | 매 테스트마다 임시 MySQL container 사용 |

## 남은 범위

이번 smoke는 backend schema/migration/FULLTEXT 중심이다.
collector upsert, Batch job end-to-end, 대량 fixture 기반 index 성능 검증은 별도 작업에서 다룬다.
