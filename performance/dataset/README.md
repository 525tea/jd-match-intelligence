# Performance Dataset Profile

이 디렉터리는 k6 성능 측정 전에 사용할 전용 데이터셋과 runtime profile을 준비한다.

목표는 실제 수집/운영 DB를 오염시키지 않고, 반복 가능한 성능 테스트 기준선을 만드는 것이다.

## 구성

- `performance.env.example`: 성능 데이터셋 실행 환경 변수 예시
- `prepare-performance-database.sh`: `jobflow_perf` DB 준비 및 fixture seed
- `seed-performance-jobs.sql`: 성능 테스트용 synthetic jobs fixture
- `performance-dataset-gate.sh`: 성능 DB 품질 gate 실행
- `performance-dataset-gate.sql`: fixture count, source, role, relation 검증
- `performance-profile-smoke.sh`: backend/gateway가 성능 DB와 성능 ES alias를 바라보는지 검증

## 1. 성능 DB 준비

w8-2 기준으로 200,000건 fixture를 준비한다.

```bash
PERF_JOB_COUNT=200000 RESET_PERF_DB=true \
bash performance/dataset/prepare-performance-database.sh
```

`RESET_PERF_DB=true`는 기존 `jobflow_perf` DB를 DROP 후 재생성한다.
기존 DB를 유지하면서 추가 삽입만 하려면 `RESET_PERF_DB=false`(기본값)로 실행한다.

기대 결과:

```text
Performance database preparation completed.
```

주의: `PERF_DB_NAME=jobflow`처럼 실제 앱 DB를 지정하면 스크립트가 중단된다.

fixture 배포 특성:
- source: `perf_fixture`
- role 분포: BACKEND 25%, FRONTEND 15%, DEVOPS 10%, DATA_ENGINEER 10%, 기타 40%
- location 분포: Seoul 70%, Gyeonggi(판교) 15%, 기타 15%
- deadline null 비율: 약 9% (MOD(n, 11) = 0)
- description에 역할별 기술 스택 키워드 포함 (FULLTEXT 검색 가능)

## 2. Dataset Gate 실행

성능 fixture가 의도한 분포와 enum 값을 만족하는지 확인한다.

```bash
bash performance/dataset/performance-dataset-gate.sh
```

기대 결과:

```text
Performance dataset gate completed.
```

확인 항목:

- performance job count
- job skill link count
- job experience tag link count
- unexpected source count
- unexpected role count
- source distribution
- role distribution
- open job count
- null deadline count

## 3. Performance Profile로 backend/gateway 기동

성능 테스트는 기본 앱 DB가 아니라 `jobflow_perf` DB와 `jobflow-jobs-performance` Elasticsearch alias를 사용한다.

```bash
PERF_DB_NAME=jobflow_perf \
docker compose -f docker-compose.yml -f docker-compose.performance.yml up -d backend gateway
```

수정된 backend image가 필요하면 먼저 build한다.

```bash
docker compose -f docker-compose.yml -f docker-compose.performance.yml build backend
```

기대 설정:

- `SPRING_PROFILES_ACTIVE=local,performance`
- backend datasource: `jobflow_perf`
- Elasticsearch alias: `jobflow-jobs-performance`
- Elasticsearch physical index: `jobflow-jobs-performance-v1`
- startup reindex: enabled

## 4. Backend Reindex 확인

performance profile 기동 후 backend 로그에서 1,000건 reindex가 완료됐는지 확인한다.

```bash
docker compose logs --tail=160 backend | grep -Ei 'reindex|indexedCount|Application run failed'
```

기대 결과:

```text
Job search reindex batch completed. indexedCount=500
Job search reindex batch completed. indexedCount=1000
Job search reindex completed. indexedCount=1000
```

## 5. Performance Profile Smoke

API 목록/검색이 실제로 performance fixture를 반환하는지 확인한다.

```bash
EXPECTED_MIN_RESULT_COUNT=1 \
BASE_URL=http://localhost:8081/api \
bash performance/dataset/performance-profile-smoke.sh
```

기대 결과:

```text
jobs_result_count=20
jobs_perf_external_id_count=20
jobs_search_success=true
jobs_search_count=5
jobs_search_perf_external_id_count=5
Performance profile smoke completed.
```

`jobs_perf_external_id_count` 또는 `jobs_search_perf_external_id_count`가 0이면 backend가 성능 DB/ES alias가 아닌 다른 데이터셋을 보고 있는 것이다.

## 6. k6 Smoke

k6가 설치되어 있으면 로컬에서 실행한다.

```bash
BASE_URL=http://localhost:8081/api \
VUS=1 \
DURATION=10s \
k6 run performance/k6/job-search-performance-profile.js
```

k6가 설치되어 있지 않으면 Docker로 실행한다.

```bash
docker run --rm \
  -e BASE_URL=http://host.docker.internal:8081/api \
  -e VUS=1 \
  -e DURATION=10s \
  -v "$PWD/performance/k6:/scripts" \
  grafana/k6 run /scripts/job-search-performance-profile.js
```

기대 결과:

```text
checks_succeeded: 100.00%
checks_failed: 0.00%
http_req_failed rate=0.00%
```

## 문제 해결

### backend가 Elasticsearch alias 오류로 뜨지 않는 경우

예시:

```text
alias [jobflow-jobs] has more than one write index
```

원인:

- performance profile이 켜지지 않아 기본 alias `jobflow-jobs`와 performance index가 섞였을 가능성이 높다.

확인:

```bash
docker compose -f docker-compose.yml -f docker-compose.performance.yml config \
  | grep -E 'SPRING_PROFILES_ACTIVE|ELASTICSEARCH_JOBS_ALIAS|ELASTICSEARCH_JOBS_INDEX'
```

기대값:

```text
SPRING_PROFILES_ACTIVE: local,performance
ELASTICSEARCH_JOBS_ALIAS: jobflow-jobs-performance
ELASTICSEARCH_JOBS_INDEX: jobflow-jobs-performance-v1
```

### `/jobs`는 performance fixture를 보지만 `/jobs/search`는 실데이터를 보는 경우

원인:

- DB는 `jobflow_perf`를 보지만 Elasticsearch alias가 performance alias가 아니거나 reindex가 완료되지 않았다.

해결:

```bash
PERF_DB_NAME=jobflow_perf \
docker compose -f docker-compose.yml -f docker-compose.performance.yml up -d backend gateway
```

그 다음 backend 로그에서 reindex 완료를 확인한다.

## 다음 단계

이 profile이 통과하면 k6 Round 1에서 다음 API를 기준으로 baseline을 측정한다.

- 공고 목록
- 공고 검색
- 추천
- 갭 분석
