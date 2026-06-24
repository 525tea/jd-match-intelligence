# Performance Dataset/Profile Report

## 목적

k6 Round 1을 실행하기 전에 운영/수집 DB와 성능 테스트 DB를 분리하고, 공고 목록/검색 API가 성능 전용 데이터셋과 Elasticsearch alias를 바라보는지 검증했다.

이번 작업의 핵심은 실제 수집 공고 품질 DB를 오염시키지 않고, 반복 가능한 성능 측정 환경을 만드는 것이다.

## 구성

### 성능 테스트 DB

- database: `jobflow_perf`
- fixture count: 1,000 jobs
- source mix:
  - `JUMPIT`: 333
  - `SEARCH_BASELINE`: 333
  - `WANTED`: 334
- role count: 7
- open jobs: 950
- null deadline jobs: 100
- job skill links: 2,000
- job experience tag links: 1,000

### 성능 테스트 Elasticsearch

- alias: `jobflow-jobs-performance`
- physical index: `jobflow-jobs-performance-v1`
- startup reindex: enabled
- reindex batch size: 500
- indexed job count: 1,000

운영/로컬 기본 검색 alias인 `jobflow-jobs`와 분리해 성능 profile 실행 중에도 기존 검색 인덱스를 오염시키지 않도록 했다.

## 검증 결과

### Dataset Gate

```text
check_name      database_name   perf_job_count  skill_link_count  tag_link_count  unexpected_source_count  unexpected_role_count  source_count  role_count  open_job_count  null_deadline_count
PERFORMANCE_DATASET_SUMMARY     jobflow_perf    1000            2000              1000            0                        0                      3             7           950             100

source  job_count
JUMPIT  333
SEARCH_BASELINE 333
WANTED  334

role    job_count
BACKEND 125
DATA_ENGINEER   125
DEVOPS  250
FRONTEND        125
FULLSTACK       125
ML_ENGINEER     125
SECURITY        125

Performance dataset gate completed.
```

### Backend Reindex

```text
Job search reindex batch completed. indexedCount=500, lastJobId=500, batchSize=500
Job search reindex batch completed. indexedCount=1000, lastJobId=1000, batchSize=500
Job search reindex completed. indexedCount=1000
```

### Performance Profile Smoke

```text
BASE_URL=http://localhost:8081/api
EXPECTED_MIN_RESULT_COUNT=1
jobs_result_count=20
jobs_perf_external_id_count=20
jobs_search_success=true
jobs_search_count=5
jobs_search_perf_external_id_count=5
Performance profile smoke completed.
```

### k6 Smoke

```text
checks_succeeded: 100.00% 56 out of 56
checks_failed: 0.00% 0 out of 56

✓ jobs list status is 200
✓ jobs list returns data array
✓ jobs list uses performance fixture
✓ jobs search status is 200
✓ jobs search success is true
✓ jobs search returns data array
✓ jobs search uses performance fixture

http_req_failed rate=0.00%
jobs_list p95=235.05ms
jobs_search p95=1.07s
```

## 확인한 리스크와 보완

### Elasticsearch alias 충돌

초기 실행 중 `jobflow-jobs` alias가 성능 인덱스와 운영 인덱스에 동시에 write index로 연결되는 문제가 발생했다.

원인은 성능 profile 실행 시 DB는 `jobflow_perf`를 바라보지만, 검색 alias는 기존 `jobflow-jobs`를 계속 바라보는 설정 불일치였다.

이를 해결하기 위해 다음을 적용했다.

- `ELASTICSEARCH_JOBS_ALIAS`로 검색 alias 외부 주입 가능
- `application-performance.yml` 추가
- `docker-compose.performance.yml`에서 `PERF_SPRING_PROFILES_ACTIVE=local,performance` 기본 적용
- 성능 전용 alias `jobflow-jobs-performance` 사용
- 성능 profile 부팅 시 1,000건 reindex 검증

### Role enum mismatch

초기 fixture에 실제 enum에 없는 `ML_AI` 값이 들어가 API 500이 발생했다.

이를 `ML_ENGINEER`로 수정했고, dataset gate에 role allow-list 검증을 추가해 같은 문제가 늦게 터지지 않도록 했다.

## 다음 단계

- k6 Round 1에서 공고 목록/검색/추천/갭분석 API baseline 측정
- Grafana/Prometheus에서 latency, error rate, JVM/DB/ES 지표 캡처
- 병목 지점 기록 후 Kafka 도입 전후 비교 기준선으로 활용
