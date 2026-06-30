# k6 Performance Scripts

## Round 1 Monolith Baseline

Staging/performance stack이 준비된 상태에서 공고 목록, 검색, 추천, 갭 분석 API를 함께 측정한다.

Round 1 baseline은 애플리케이션 처리 성능을 보기 위한 측정이므로 Gateway의 운영용 fixed-window rate limit을 측정 대상에서 제외한다. `docker-compose.performance.yml`은 기본적으로 `PERF_GATEWAY_RATE_LIMIT_ENABLED=false`를 적용한다. rate limit 자체를 검증할 때는 `performance/gateway/gateway-smoke.sh`를 별도로 사용한다.

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

서버에서 실행한 k6 summary JSON을 로컬 artifact 폴더로 가져오려면 로컬 터미널에서 다음을 실행한다. `SSH_HOST`는 현재 EC2 public IP로 바꾼다.

```bash
SSH_HOST=ubuntu@3.38.220.29 \
VUS=20 \
DURATION=10m \
ARTIFACT_NAME=k6_round1_20vu_10m_summary_passed.json \
bash performance/k6/collect-round1-artifact.sh
```

기본 로컬 저장 위치는 `/Users/iyejin/dev/jobflow-server-env/artifacts/260625_k6_round1/`이다. 이 폴더는 로컬 보관용이며 Git에 커밋하지 않는다.

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

## Elasticsearch No-cache Stress Test

`jobflow_perf` database에 200k 공고 fixture를 준비하고, cache를 끈 상태에서 Elasticsearch 검색 경로의 한계를 측정한다. 이 테스트는 Gateway circuit breaker나 rate limit의 영향을 빼고 ES 검색 성능을 보기 위한 작업이므로 backend를 직접 타격한다.

사전 준비:

```bash
git pull --rebase

docker compose -f docker-compose.yml -f docker-compose.performance.yml build backend gateway

PERF_CACHE_ENABLED=true \
REQUIRED_PORTS="" \
bash performance/deploy/staging-performance-up.sh
```

performance compose profile은 stress test 중 Elasticsearch health contributor timeout이 backend 컨테이너 health를 흔들지 않도록 다음 기준을 사용한다.

- backend Docker healthcheck는 `/actuator/health/liveness`를 본다.
- performance profile에서는 `management.health.elasticsearch.enabled=false`를 기본값으로 둔다.
- ES 검색 API 자체는 계속 Elasticsearch를 사용하므로, health contributor만 lifecycle 판단에서 제외된다.

backend가 `unhealthy`로 끝나면 바로 k6를 실행하지 않는다. 먼저 stale image인지, app startup failure인지, healthcheck failure인지 분리한다.

```bash
docker compose -f docker-compose.yml -f docker-compose.performance.yml ps -a backend elasticsearch

docker inspect jobflow-backend --format '{{json .State.Health}}' | jq

docker compose -f docker-compose.yml -f docker-compose.performance.yml logs --tail=300 backend

docker exec jobflow-backend sh -lc '
  curl -i --max-time 5 http://localhost:8080/actuator/health/liveness || true
  echo
  curl -i --max-time 5 http://localhost:8080/actuator/health/readiness || true
  echo
  curl -i --max-time 10 http://localhost:8080/actuator/health || true
'
```

ES 상태 확인:

```bash
curl -s "http://localhost:9200/_cluster/health" | python3 -m json.tool
curl -s "http://localhost:9200/_cat/nodes?v&h=name,heap.percent,ram.percent,cpu"
curl -s "http://localhost:9200/jobflow-jobs-performance-v1/_count" | python3 -m json.tool
```

정상 진행 기준:

- backend liveness가 `UP`
- ES index count가 `200000`
- backend 직접 검색 API smoke가 응답

```bash
curl -s http://localhost:8080/actuator/health/liveness | jq
curl -s "http://localhost:8080/jobs/search?keyword=java&limit=10" | head -c 300
```

단건 smoke:

```bash
k6 run \
  --vus 1 \
  --iterations 1 \
  -e BASE_URL=http://localhost:8080 \
  performance/k6/stress-es-nocache-200k.js
```

본 테스트:

```bash
bash performance/k6/run-stress-es-nocache.sh
```

기본값:

- `BASE_URL=http://localhost:8080`
- `ARTIFACT_DIR=artifacts/performance`
- `SUMMARY_FILE=YYMMDD_k6_es_nocache_200k_500vu.json`

확인할 지표:

- `http_req_duration{endpoint:jobs_search} p(95), p(99)`
- `http_req_failed`
- `checks`
- Elasticsearch node heap/CPU
- Grafana JVM memory, HTTP request rate, latency

## Elasticsearch + Redis Cache Stress Test

`jobflow_perf` database에 200k 공고 fixture를 준비하고, `CACHE_ENABLED=true` 상태에서 Redis cache hit이 ES 검색 경로 latency에 미치는 효과를 측정한다. No-cache 결과와의 before/after 비교가 목적이므로 동일하게 backend를 직접 타격한다.

사전 준비:

```bash
git pull --rebase

docker compose -f docker-compose.yml -f docker-compose.performance.yml build backend gateway

REQUIRED_PORTS="" \
bash performance/deploy/staging-performance-up.sh
```

서버 기동 후 `CACHE_ENABLED=true`가 적용됐는지 확인:

```bash
docker compose -f docker-compose.yml -f docker-compose.performance.yml exec backend env | grep CACHE_ENABLED
```

ES 상태 확인:

```bash
curl -s "http://localhost:9200/_cluster/health" | python3 -m json.tool
curl -s "http://localhost:9200/jobflow-jobs-performance-v1/_count" | python3 -m json.tool
```

정상 진행 기준:

- backend liveness가 `UP`
- ES index count가 `200000`
- `CACHE_ENABLED=true` 환경변수 확인

```bash
curl -s http://localhost:8080/actuator/health/liveness | jq
curl -s "http://localhost:8080/jobs/search?keyword=java&limit=10" | head -c 300
```

단건 smoke:

```bash
k6 run \
  --vus 1 \
  --iterations 1 \
  -e BASE_URL=http://localhost:8080 \
  performance/k6/stress-es-cache-200k.js
```

본 테스트:

```bash
bash performance/k6/run-stress-es-cache.sh
```

기본값:

- `BASE_URL=http://localhost:8080`
- `ARTIFACT_DIR=artifacts/performance`
- `SUMMARY_FILE=YYMMDD_k6_es_cache_200k_500vu.json`

threshold:

- `http_req_duration{endpoint:jobs_search} p(95) < 10000ms` (no-cache 대비 60000ms → 10000ms)
- `http_req_failed rate < 0.50`

확인할 지표:

- `http_req_duration{endpoint:jobs_search} p(95), p(99)`
- `http_req_failed`
- `checks`
- Redis cache hit rate (`cache_gets_total{result="hit"}`)
- Grafana JVM memory, HTTP request rate, latency

## Elasticsearch + Redis Mixed Hit-rate Stress Test

High cache-hit stress test는 10개 인기 키워드를 반복 조회해 Redis cache가 거의 모든 검색 요청을 흡수하는 조건을 검증한다. mixed hit-rate stress test는 인기 키워드와 long-tail 검색어를 섞어 cache hit rate가 낮아질 때 p95/p99, RPS, error rate, Redis hit/miss가 어떻게 변하는지 확인한다.

사전 준비는 Elasticsearch + Redis cache stress test와 동일하다.

```bash
PERF_CACHE_ENABLED=true \
PERF_TRACING_SAMPLING_PROBABILITY=0.0 \
PERF_MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED=false \
ELASTICSEARCH_REINDEX_ON_STARTUP=false \
PERF_ELASTICSEARCH_MEMORY_LIMIT=3g \
PERF_ES_JAVA_OPTS="-Xms2g -Xmx2g" \
REQUIRED_PORTS="" \
bash performance/deploy/staging-performance-up.sh
```

단건 smoke:

```bash
k6 run \
  --vus 1 \
  --iterations 1 \
  -e BASE_URL=http://localhost:8080 \
  -e HOT_TRAFFIC_PERCENT=70 \
  performance/k6/stress-es-cache-mixed-hit-rate-200k.js
```

70% hot / 30% long-tail:

```bash
HOT_TRAFFIC_PERCENT=70 \
SUMMARY_FILE=260630_k6_es_cache_mixed_70_hot_200k_500vu.json \
bash performance/k6/run-stress-es-cache-mixed-hit-rate.sh
```

50% hot / 50% long-tail:

```bash
HOT_TRAFFIC_PERCENT=50 \
SUMMARY_FILE=260630_k6_es_cache_mixed_50_hot_200k_500vu.json \
bash performance/k6/run-stress-es-cache-mixed-hit-rate.sh
```

30% hot / 70% long-tail:

```bash
HOT_TRAFFIC_PERCENT=30 \
SUMMARY_FILE=260630_k6_es_cache_mixed_30_hot_200k_500vu.json \
bash performance/k6/run-stress-es-cache-mixed-hit-rate.sh
```

각 ratio 실행은 cold cache 기준으로 비교해야 한다. `run-stress-es-cache-mixed-hit-rate.sh`는 기본값으로 실행 직전 Redis `FLUSHDB`를 수행해 이전 70%/50%/30% 실행의 `jobSearch` cache 상태가 다음 실행에 섞이지 않게 한다. 수동으로 관리해야 하는 경우에만 `RESET_REDIS_CACHE_BEFORE_RUN=false`로 끄고, ratio 사이에 Redis flush 또는 stack restart를 별도로 수행한다.

기본값:

- `BASE_URL=http://localhost:8080`
- `ARTIFACT_DIR=artifacts/performance`
- `HOT_TRAFFIC_PERCENT=70`
- `LONG_TAIL_RUN_ID=$(date +%Y%m%d%H%M%S)`
- `LONG_TAIL_VARIANTS=1000000` (VU별 long-tail variant block 크기)
- `RESET_REDIS_CACHE_BEFORE_RUN=true`
- `SUMMARY_FILE=YYMMDD_k6_es_cache_mixed_${HOT_TRAFFIC_PERCENT}_hot_200k_500vu.json`

확인할 지표:

- `http_req_duration{endpoint:jobs_search,traffic:hot} p(95), p(99)`
- `http_req_duration{endpoint:jobs_search,traffic:long_tail} p(95), p(99)`
- `http_req_failed`
- Redis cache hit/miss rate (`cache_gets_total{result="hit|miss"}`)
- Grafana JVM memory, HTTP request rate, latency

## Analysis API Cache Before/After Stress Test

검색 API가 아니라 사용자별 분석 API 캐시 효과를 비교한다. 대상은 `gapAnalysis`, `jdMatch`, `jobRecommendation` cache를 사용하는 다음 endpoint다.

- `GET /gap-analysis/projects/{userProjectId}`
- `GET /projects/{userProjectId}/job-matches`
- `GET /recommendations/jobs`

before/after는 같은 k6 workload로 비교하고, 서버의 cache env만 바꾼다.

before stack:

```bash
PERF_CACHE_ENABLED=false \
PERF_TRACING_SAMPLING_PROBABILITY=0.0 \
PERF_MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED=false \
ELASTICSEARCH_REINDEX_ON_STARTUP=false \
PERF_ELASTICSEARCH_MEMORY_LIMIT=3g \
PERF_ES_JAVA_OPTS="-Xms2g -Xmx2g" \
REQUIRED_PORTS="" \
bash performance/deploy/staging-performance-up.sh
```

before run:

```bash
CACHE_MODE=disabled \
SUMMARY_FILE=260630_k6_analysis_cache_disabled_200vu.json \
bash performance/k6/run-stress-analysis-cache.sh
```

after stack:

```bash
PERF_CACHE_ENABLED=true \
PERF_TRACING_SAMPLING_PROBABILITY=0.0 \
PERF_MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED=false \
ELASTICSEARCH_REINDEX_ON_STARTUP=false \
PERF_ELASTICSEARCH_MEMORY_LIMIT=3g \
PERF_ES_JAVA_OPTS="-Xms2g -Xmx2g" \
REQUIRED_PORTS="" \
bash performance/deploy/staging-performance-up.sh
```

after run:

```bash
CACHE_MODE=enabled \
SUMMARY_FILE=260630_k6_analysis_cache_enabled_200vu.json \
bash performance/k6/run-stress-analysis-cache.sh
```

기본값:

- `BASE_URL=http://localhost:8080`
- `ENDPOINTS=gap_analysis,jd_match,recommendations_jobs`
- `TARGET_ROLES=BACKEND,DATA_ENGINEER,DEVOPS`
- `TARGET_CAREER_LEVELS=JUNIOR,MID,SENIOR`
- `VUS=200`
- `DURATION=10m`
- `LIMIT=20`
- `SLEEP_SECONDS=1`
- `ARTIFACT_DIR=artifacts/performance`
- `RESET_REDIS_CACHE_BEFORE_RUN=true`

확인할 지표:

- `http_req_duration{endpoint:gap_analysis} p(95), p(99)`
- `http_req_duration{endpoint:jd_match} p(95), p(99)`
- `http_req_duration{endpoint:recommendations_jobs} p(95), p(99)`
- `http_req_failed`
- `checks`
- Redis cache hit/miss rate for `gapAnalysis`, `jdMatch`, `jobRecommendation`
- Grafana JVM memory, HTTP request rate, latency, HikariCP active/pending

## Elasticsearch + Redis Capacity Rework

Mixed hit-rate stress test는 각 VU가 1초 pacing으로 반복 요청하므로 500VU 조건에서 약 500 RPS plateau가 자연스럽다. saturation stress test는 이 pacing을 제거하고 k6 `constant-arrival-rate` executor로 `/jobs/search` cache-hit 중심 처리량 상한을 별도로 측정한다.

이 테스트의 목표는 3000 RPS를 목표로, 1000 → 1500 → 2000 → 2500 → 3000 RPS 단계에서 안정 구간, 경계 구간, 포화 구간을 분리하는 것이다. 각 단계에서 error rate, p95/p99, dropped iterations, k6 VU 사용량, JVM heap, Tomcat/Hikari, Redis/ES 지표를 함께 기록한다.

`/jobs/search`는 public API이므로 saturation runner는 기본적으로 `SEARCH_AUTH_MODE=public`으로 실행한다. Authorization 헤더를 붙이면 `JwtAuthenticationFilter`가 매 요청마다 사용자 조회를 수행해 HikariCP/DB 비용이 검색 cache-hit 경로에 섞인다. 인증 포함 검색 성능은 `SEARCH_AUTH_MODE=login` 또는 `SEARCH_AUTH_MODE=token`으로 별도 측정한다.

안정 처리량으로 인정하려면 k6 threshold 통과뿐 아니라 `dropped_iterations=0`이어야 한다. runner는 기본값 `MAX_DROPPED_ITERATIONS=0`으로 summary JSON을 검증한다. dropped iteration이 발생한 run은 error rate가 0이고 p95 기준을 통과하더라도 경계/포화 증거로만 기록한다.

사전 준비는 Elasticsearch + Redis cache stress test와 동일하다. 3000 RPS 시도 전에는 backend가 cache enabled 상태이고 200k index가 이미 준비되어 있어야 한다.

```bash
PERF_CACHE_ENABLED=true \
PERF_MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED=false \
ELASTICSEARCH_REINDEX_ON_STARTUP=false \
PERF_ELASTICSEARCH_MEMORY_LIMIT=3g \
PERF_ES_JAVA_OPTS="-Xms2g -Xmx2g" \
REQUIRED_PORTS="" \
bash performance/deploy/staging-performance-up.sh
```

기존 realistic workload 기준선을 다시 확인하려면 mixed hit-rate runner를 먼저 실행한다.

```bash
HOT_TRAFFIC_PERCENT=70 \
SUMMARY_FILE=260630_k6_es_cache_mixed_70_hot_200k_500vu.json \
bash performance/k6/run-stress-es-cache-mixed-hit-rate.sh
```

saturation 단계별 실행:

```bash
TARGET_RPS_LIST=1000,2000,3000 \
DURATION=2m \
PRE_ALLOCATED_VUS=800 \
MAX_VUS=4000 \
SUMMARY_PREFIX=260630_k6_es_cache_saturation_200k \
bash performance/k6/run-stress-es-cache-saturation.sh
```

### Internal runner

EC2 서버 내부에서 backend direct port를 호출하는 방식이다. MySQL fixture count, Elasticsearch document count, backend cache env를 Docker Compose로 직접 확인한다.

```bash
TARGET_RPS_LIST=1000,1500,2000,2500,3000 \
DURATION=5m \
PRE_ALLOCATED_VUS=800 \
MAX_VUS=4000 \
RUN_LOCATION=internal \
RUN_LABEL=capacity_rework \
SEARCH_AUTH_MODE=public \
bash performance/k6/run-stress-es-cache-saturation.sh
```

### External runner

로컬 Mac 또는 별도 load generator에서 EC2 public endpoint를 호출하는 방식이다. 이 경우 로컬에서 서버의 Docker Compose에 접근할 수 없으므로 Compose 기반 preflight는 끈다. 서버 내부에서는 별도로 MySQL 200k, Elasticsearch 200k, `CACHE_ENABLED=true`를 확인한 뒤 실행한다.

```bash
BASE_URL=http://EC2_PUBLIC_IP:8080 \
PROMETHEUS_URL=http://EC2_PUBLIC_IP:8080/actuator/prometheus \
HEALTH_URL=http://EC2_PUBLIC_IP:8080/actuator/health/liveness \
LOCAL_COMPOSE_PREFLIGHT=false \
ES_COUNT_PREFLIGHT=false \
TARGET_RPS_LIST=1000,1500,2000,2500,3000 \
DURATION=5m \
PRE_ALLOCATED_VUS=800 \
MAX_VUS=4000 \
RUN_LOCATION=external \
RUN_LABEL=capacity_rework \
SEARCH_AUTH_MODE=public \
bash performance/k6/run-stress-es-cache-saturation.sh
```

External runner에서도 search preflight와 cache hit delta preflight는 유지한다. 즉, 서버 env를 직접 읽지 못하더라도 `/jobs/search` warmup 이후 `jobSearch` cache hit가 증가하지 않으면 본 테스트로 들어가지 않는다.

### Cold/mixed/spike follow-up runner

`run-stress-es-cache-capacity-followup.sh`는 hot-cache 성공 수치를 cold/mixed/spike 조건으로 일반화하지 않기 위해 cache 상태를 명시적으로 분리한다.

| scenario | cache 준비 | 목적 |
| --- | --- | --- |
| `hot` | Redis reset 후 hot keyword warmup | Redis hot-cache path의 안정 처리량 확인 |
| `cold` | Redis reset, warmup 없음 | miss burst에서 ES/search path와 WAS 지표가 먼저 흔들리는 지점 확인 |
| `mixed` | Redis reset 후 hot keyword만 warmup | hot traffic과 long-tail miss traffic이 섞일 때 hit/miss ratio와 tail latency 확인 |
| `spike` | Redis reset 후 hot keyword warmup | `BASE_RPS -> TARGET_RPS` 급상승 profile에서 dropped iteration과 latency 확인 |

application stack 내부에서 실행할 때는 runner가 Redis `FLUSHDB`, warmup, Prometheus snapshot, cache hit/miss delta 검증을 모두 수행한다.

```bash
CAPACITY_SCENARIOS=hot,cold,mixed,spike \
TARGET_RPS_LIST=1000 \
DURATION=2m \
BASE_RPS=500 \
SPIKE_RAMP_DURATION=10s \
SPIKE_HOLD_DURATION=2m \
PRE_ALLOCATED_VUS=800 \
MAX_VUS=4000 \
RUN_LOCATION=internal \
RUN_LABEL=capacity_followup \
bash performance/k6/run-stress-es-cache-capacity-followup.sh
```

별도 k6 runner에서 실행할 때는 runner가 application host의 Docker Compose/Redis에 접근할 수 없다. 따라서 각 scenario 실행 직전에 application host에서 Redis를 먼저 비우고, 외부 runner에서는 `RESET_REDIS_CACHE_BEFORE_RUN=false`로 실행한다. 이 절차를 생략하면 이전 run의 `jobSearch` cache가 섞여 cold/mixed 결과를 신뢰할 수 없다.

application host:

```bash
cd ~/jobflow
docker compose -f docker-compose.yml -f docker-compose.performance.yml exec -T redis redis-cli FLUSHDB
```

external k6 runner:

```bash
BASE_URL=http://APP_EC2_PUBLIC_IP:8080 \
PROMETHEUS_URL=http://APP_EC2_PUBLIC_IP:8080/actuator/prometheus \
HEALTH_URL=http://APP_EC2_PUBLIC_IP:8080/actuator/health/liveness \
LOCAL_COMPOSE_PREFLIGHT=false \
ES_COUNT_PREFLIGHT=false \
RESET_REDIS_CACHE_BEFORE_RUN=false \
CACHE_RESET_MODE=skip \
CAPACITY_SCENARIOS=mixed \
TARGET_RPS_LIST=1000 \
HOT_TRAFFIC_PERCENT=70 \
DURATION=2m \
PRE_ALLOCATED_VUS=800 \
MAX_VUS=4000 \
RUN_LOCATION=k6-runner \
RUN_LABEL=capacity_followup \
bash performance/k6/run-stress-es-cache-capacity-followup.sh
```

`cold`, `mixed`, `spike`를 비교할 때는 scenario를 하나씩 실행하고 매번 Redis reset을 다시 수행한다. 여러 scenario를 한 번에 실행하는 방식은 application host 내부 실행처럼 runner가 reset을 직접 수행할 수 있을 때만 사용한다.

follow-up runner 주요 기본값:

- `CAPACITY_SCENARIOS=hot,cold,mixed,spike`
- `TARGET_RPS_LIST=1000`
- `BASE_RPS=500`
- `DURATION=2m`
- `SPIKE_RAMP_DURATION=10s`
- `SPIKE_HOLD_DURATION=2m`
- `HOT_TRAFFIC_PERCENT=70`
- `LONG_TAIL_VARIANTS=1000000`
- `MAX_DROPPED_ITERATIONS=0`
- `RESET_REDIS_CACHE_BEFORE_RUN=true`

기존 saturation runner 기본값:

- `BASE_URL=http://localhost:8080`
- `ARTIFACT_DIR=artifacts/performance`
- `TARGET_RPS_LIST=1000,1500,2000,2500,3000`
- `DURATION=5m`
- `PRE_ALLOCATED_VUS=800`
- `MAX_VUS=4000`
- `P95_THRESHOLD_MS=1000`
- `FAIL_RATE_THRESHOLD=0.01`
- `MAX_DROPPED_ITERATIONS=0`
- `RUN_LOCATION=internal`
- `RUN_LABEL=capacity_rework`
- `SEARCH_AUTH_MODE=public`
- `SUMMARY_PREFIX=YYMMDD_k6_es_cache_capacity_${RUN_LABEL}_${RUN_LOCATION}_200k`

각 RPS run은 k6 summary JSON과 함께 Prometheus metric snapshot을 저장한다.

- `*_1000rps.json`
- `*_1000rps_prometheus_before.prom`
- `*_1000rps_prometheus_after.prom`

Saturation workload는 순수 `/jobs/search` 처리량 상한을 보기 위한 테스트이므로 performance stack의 tracing sampling은 기본 `0.0`으로 둔다. Zipkin/trace export 비용까지 포함한 관측성 부하 테스트가 필요하면 `PERF_TRACING_SAMPLING_PROBABILITY=1.0`을 별도 케이스로 분리해서 실행한다.

OS/WAS 튜닝은 saturation baseline을 먼저 찍은 뒤 적용한다. 서버에서 적용 전 값을 반드시 저장한다.

```bash
SNAPSHOT_FILE=260630_before_saturation_tuning.txt \
bash performance/deploy/performance-host-tuning-snapshot.sh
```

OS sysctl 적용은 기본 dry-run이다. 실제 적용할 때만 `APPLY=true`를 붙인다.

```bash
bash performance/deploy/performance-host-tuning-apply.sh

APPLY=true \
bash performance/deploy/performance-host-tuning-apply.sh
```

Tomcat/Hikari/JVM tuning은 `docker-compose.performance.yml`의 `PERF_*` 환경변수로 조정한다. cache-hit 중심 `/jobs/search`에서는 DB pool보다 k6 generator, Tomcat connection/thread queue, Redis, CPU 한계를 먼저 본다.

```bash
PERF_CACHE_ENABLED=true \
PERF_SERVER_TOMCAT_THREADS_MAX=400 \
PERF_SERVER_TOMCAT_THREADS_MIN_SPARE=50 \
PERF_SERVER_TOMCAT_ACCEPT_COUNT=1000 \
PERF_SERVER_TOMCAT_MAX_CONNECTIONS=12000 \
PERF_HIKARI_MAXIMUM_POOL_SIZE=30 \
PERF_HIKARI_MINIMUM_IDLE=10 \
PERF_TRACING_SAMPLING_PROBABILITY=0.0 \
PERF_BACKEND_MEMORY_LIMIT=2g \
PERF_BACKEND_JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0" \
PERF_MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED=false \
ELASTICSEARCH_REINDEX_ON_STARTUP=false \
REQUIRED_PORTS="" \
bash performance/deploy/staging-performance-up.sh
```

튜닝 적용 후에도 snapshot을 남긴다.

```bash
SNAPSHOT_FILE=260630_after_saturation_tuning.txt \
bash performance/deploy/performance-host-tuning-snapshot.sh
```

확인할 지표:

- k6 `http_reqs`, `http_req_failed`, `http_req_duration p(95)/p(99)`
- k6 `dropped_iterations`
- k6 `vus`, `vus_max`가 `MAX_VUS`에 닿는지 여부
- Grafana `/jobs/search` RPS와 p95/p99
- JVM heap/GC, Tomcat request latency
- Hikari active/pending connection
- Redis cache hit rate
- Elasticsearch indexing/search 관련 지표

판정 기준:

- 3000 RPS 성공: error rate 1% 미만, checks 99% 초과, p95/p99와 Grafana 캡처를 함께 기록한다.
- 3000 RPS 실패: 최대 안정 RPS와 병목 근거를 기록한다. 예를 들어 dropped iterations가 먼저 증가하면 k6 generator 한계, backend CPU/heap이 포화되면 WAS/instance 한계, cache miss가 늘면서 ES latency가 증가하면 검색 backend 한계로 분리한다.
