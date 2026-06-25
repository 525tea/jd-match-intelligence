# k6 Performance Scripts

## Round 1 Monolith Baseline

Staging/performance stack이 준비된 상태에서 공고 목록, 검색, 추천, 갭 분석 API를 함께 측정한다.

사전 준비:

```bash
REQUIRED_PORTS="" \
bash performance/deploy/staging-performance-up.sh
```

Round 1은 인증이 필요한 추천/갭 분석 API까지 포함하므로 `ACCESS_TOKEN`과 `USER_PROJECT_ID`를 넘기는 방식을 기본으로 한다.

```bash
BASE_URL=http://localhost:8081/api \
ACCESS_TOKEN='...' \
USER_PROJECT_ID='...' \
VUS=20 \
DURATION=10m \
bash performance/k6/run-round1-baseline.sh
```

토큰 대신 로그인 환경변수를 넘겨 k6 setup 단계에서 토큰과 최신 프로젝트 id를 가져올 수도 있다. 실제 계정 값은 로컬/서버 환경에서만 주입하고 문서나 PR에 기록하지 않는다.

```bash
BASE_URL=http://localhost:8081/api \
LOGIN_EMAIL='user@example.com' \
LOGIN_PASSWORD='password' \
VUS=20 \
DURATION=10m \
bash performance/k6/run-round1-baseline.sh
```

로컬 smoke처럼 인증 API를 제외하고 목록/검색만 빠르게 확인하려면 다음처럼 실행한다.

```bash
BASE_URL=http://localhost:8081/api \
REQUIRE_AUTH_ENDPOINTS=false \
VUS=1 \
DURATION=30s \
bash performance/k6/run-round1-baseline.sh
```

확인할 지표:

- `http_req_duration{endpoint:jobs_list} p(95), p(99)`
- `http_req_duration{endpoint:jobs_search} p(95), p(99)`
- `http_req_duration{endpoint:recommendations_jobs} p(95), p(99)`
- `http_req_duration{endpoint:gap_analysis} p(95), p(99)`
- `http_req_failed`
- `checks`
- `jobs list uses performance fixture`
- `jobs search uses performance fixture`

기본 summary export 경로는 `/tmp/jobflow-k6-round1-baseline-summary.json`이다. raw summary JSON은 커밋하지 않고, 해석 결과만 `docs/metrics/performance/`에 정리한다.

## MySQL FULLTEXT Search Baseline

Backend local profile을 실행한 상태에서 공고 검색 API의 MySQL FULLTEXT 기준선을 측정한다.

```bash
cd /Users/iyejin/dev/jobflow
k6 run performance/k6/mysql-fulltext-search-baseline.js
```

환경 변수로 조건을 바꿀 수 있다.

```bash
BASE_URL=http://localhost:8080 \
KEYWORDS='백엔드,Spring,k8s,Kubernetes' \
LIMIT=10 \
VUS=5 \
DURATION=30s \
k6 run performance/k6/mysql-fulltext-search-baseline.js
```

k6가 로컬에 설치되어 있지 않으면 Docker로 실행할 수 있다.

```bash
docker run --rm \
  -e BASE_URL=http://host.docker.internal:8080 \
  -e KEYWORDS='백엔드,Spring,k8s,Kubernetes' \
  -e LIMIT=10 \
  -e VUS=5 \
  -e DURATION=30s \
  -v "$PWD/performance/k6:/scripts" \
  grafana/k6 run /scripts/mysql-fulltext-search-baseline.js
```

확인할 지표:

- `http_req_duration p(50)`
- `http_req_duration p(95)`
- `http_req_duration p(99)`
- `http_req_failed`
- keyword별 검색 결과 유무

`k8s`와 `Kubernetes`는 MySQL FULLTEXT의 동의어 미처리 한계를 확인하기 위한 비교 키워드다.

## Performance Profile Job Search Baseline

`jobflow_perf` database와 `jobflow-jobs-performance` Elasticsearch alias가 연결된 상태에서 공고 목록/검색 API의 성능 기준선을 측정한다.

먼저 performance profile이 정상 연결됐는지 smoke를 통과시킨다.

```bash
EXPECTED_MIN_RESULT_COUNT=1 \
BASE_URL=http://localhost:8081/api \
bash performance/dataset/performance-profile-smoke.sh
```

그 다음 k6를 실행한다.

```bash
BASE_URL=http://localhost:8081/api \
VUS=10 \
DURATION=1m \
k6 run performance/k6/job-search-performance-profile.js
```

환경 변수로 조건을 바꿀 수 있다.

```bash
BASE_URL=http://localhost:8081/api \
KEYWORDS='performance,backend,data,devops,security' \
PAGE_SIZE=20 \
SEARCH_LIMIT=10 \
VUS=20 \
DURATION=3m \
SLEEP_SECONDS=1 \
k6 run performance/k6/job-search-performance-profile.js
```

k6가 로컬에 설치되어 있지 않으면 Docker로 실행할 수 있다.

```bash
docker run --rm \
  -e BASE_URL=http://host.docker.internal:8081/api \
  -e KEYWORDS='performance,backend,data,devops,security' \
  -e PAGE_SIZE=20 \
  -e SEARCH_LIMIT=10 \
  -e VUS=10 \
  -e DURATION=1m \
  -v "$PWD/performance/k6:/scripts" \
  grafana/k6 run /scripts/job-search-performance-profile.js
```

확인할 지표:

- `http_req_duration{endpoint:jobs_list} p(95), p(99)`
- `http_req_duration{endpoint:jobs_search} p(95), p(99)`
- `http_req_failed`
- `jobs list uses performance fixture`
- `jobs search uses performance fixture`

이 스크립트는 Round 1 최종 측정용이라기보다, k6 측정 전 performance DB/profile/Elasticsearch alias가 올바르게 연결됐는지 확인하는 baseline entrypoint다.
