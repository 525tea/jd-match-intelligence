# JobFlow Staging/Performance Deploy Runbook

이 문서는 staging/performance 서버에 JobFlow를 올리고, k6 성능 측정 전 운영 준비 상태를 확인하는 절차다.

## 1. 배포 대상

우선순위:

1. OCI Always Free
2. OCI capacity 또는 ARM64 image 이슈 발생 시 AWS EC2 fallback

최종 k6 수치는 로컬 Mac이 아니라 staging/performance 서버 기준으로 남긴다.

## 2. 서버 준비

서버 접속 후 먼저 bootstrap check를 실행한다.

```bash
bash performance/deploy/server-bootstrap-check.sh
```

성공 기준:

```text
Server bootstrap check completed.
```

기본 확인 기준:

- Docker Engine 실행 가능
- Docker Compose v2 사용 가능
- `curl`, `jq`, `git` 사용 가능
- 최소 메모리 3.5GB 이상
- 현재 작업 디렉터리 기준 가용 디스크 20GB 이상
- 8080, 8081, 3001, 9090, 9200, 9411 포트가 비어 있음
- `docker-compose.yml` + `docker-compose.performance.yml` config 검증 통과

필요하면 기준값을 환경변수로 조정할 수 있다.

```bash
MIN_MEMORY_MB=3000 \
MIN_DISK_AVAILABLE_MB=15000 \
REQUIRED_PORTS="8080 8081 3001 9090 9200 9411" \
bash performance/deploy/server-bootstrap-check.sh
```

로컬에서 이미 backend/gateway/observability stack이 떠 있어 포트 점유가 예상되는 경우에는 포트 체크만 건너뛸 수 있다. 실제 staging 신규 서버에서는 포트 체크를 건너뛰지 않는다.

```bash
MIN_MEMORY_MB=1 \
MIN_DISK_AVAILABLE_MB=1 \
REQUIRED_PORTS="" \
bash performance/deploy/server-bootstrap-check.sh
```

수동으로 확인해야 할 필수 도구:

```bash
docker --version
docker compose version
curl --version
jq --version
```

확인 기준:

- Docker Engine 실행 중
- Docker Compose v2 사용 가능
- `curl`, `jq` 사용 가능
- 8080, 8081, 9090, 3001, 9200, 9411 포트 정책 확인

## 3. 환경 변수 준비

서버에서만 `.env`를 만든다.

```bash
cp performance/deploy/staging.env.example .env
```

`.env`에 실제 값을 채운다.

절대 공개하면 안 되는 값:

- `MYSQL_PASSWORD`
- `MYSQL_ROOT_PASSWORD`
- `JWT_SECRET`
- `OAUTH2_PROVIDER_TOKEN_ENCRYPTION_KEY`
- `GITHUB_CLIENT_SECRET`
- `ADMIN_BOOTSTRAP_PASSWORD`
- 실제 운영 도메인에 연결된 secret 값

GitHub OAuth 관련 값 구분:

- `GITHUB_CLIENT_ID`: GitHub OAuth App에서 보이는 공개 식별자
- `GITHUB_CLIENT_SECRET`: GitHub OAuth App secret, 절대 공개 금지
- `GITHUB_OAUTH_REDIRECT_URI`: GitHub OAuth App callback URL과 정확히 일치해야 하는 backend callback
- `OAUTH2_SUCCESS_REDIRECT_URI`: OAuth 성공 후 frontend로 돌아갈 URL
- `OAUTH2_FAILURE_REDIRECT_URI`: OAuth 실패 후 frontend로 돌아갈 URL

## 4. 이미지 빌드 및 기동

staging/performance 배포는 우선 통합 스크립트로 실행한다.

```bash
bash performance/deploy/staging-performance-up.sh
```

성공 기준:

```text
Staging performance stack is ready for pre-k6 smoke.
```

이 스크립트는 다음 순서로 실행된다.

1. `.env` 존재 확인
2. 서버 bootstrap check
3. performance DB 준비와 dataset gate
4. `docker-compose.yml` + `docker-compose.performance.yml` config 검증
5. backend/gateway/elasticsearch image build
6. performance stack 기동
7. backend/gateway health 대기
8. performance reindex 완료 로그 확인
9. performance profile smoke

서버에서 이미 image를 build했거나 특정 service만 올리고 싶으면 환경변수로 조정할 수 있다.

```bash
BUILD_SERVICES="" \
UP_SERVICES="backend gateway" \
bash performance/deploy/staging-performance-up.sh
```

로컬에서 이미 포트가 점유된 상태로 스크립트 동작만 확인하려면 bootstrap check의 포트 검사를 건너뛸 수 있다. 실제 staging 신규 서버에서는 포트 체크를 건너뛰지 않는다.

```bash
REQUIRED_PORTS="" \
bash performance/deploy/staging-performance-up.sh
```

## 4-2. Pre-k6 smoke

staging/performance stack이 k6 측정에 들어갈 수 있는 상태인지 한 번에 확인한다.

```bash
BASE_URL=http://localhost:8081/api \
BACKEND_URL=http://localhost:8080 \
GATEWAY_URL=http://localhost:8081 \
PROMETHEUS_URL=http://localhost:9090 \
GRAFANA_URL=http://localhost:3001 \
ZIPKIN_URL=http://localhost:9411 \
ELASTICSEARCH_URL=http://localhost:9200 \
bash performance/deploy/staging-pre-k6-smoke.sh
```

성공 기준:

```text
Staging pre-k6 smoke completed.
```

기본 pre-k6 smoke는 성능 fixture와 성능 Elasticsearch alias가 정상 연결됐는지 검증한다. 실데이터 검색 품질을 검증하는 `search-intent-smoke.sh`는 `backend junior seoul`처럼 실제 수집 데이터 분포를 전제로 하므로, 성능 fixture 서버에서는 기본으로 건너뛴다.

실데이터 DB를 대상으로 검색 의도 품질까지 함께 확인해야 할 때만 아래처럼 켠다.

```bash
RUN_SEARCH_INTENT_SMOKE=true \
BASE_URL=http://localhost:8081/api \
bash performance/deploy/staging-pre-k6-smoke.sh
```

수동으로 실행해야 하는 경우에는 아래 절차를 따른다.

로컬/staging 서버에서 image를 직접 빌드하는 경우:

```bash
docker compose build backend gateway elasticsearch
docker compose up -d
```

기동 상태 확인:

```bash
docker compose ps
```

기대 상태:

- `mysql`: healthy
- `redis`: healthy
- `elasticsearch`: healthy
- `backend`: healthy
- `gateway`: healthy
- `prometheus`: running
- `grafana`: running
- `zipkin`: running

## 4-1. Performance DB 준비

k6 baseline은 실제 수집/운영 DB가 아니라 performance 전용 DB를 사용한다.

서버에서 `.env` 값을 채운 뒤 성능 DB를 준비한다.

```bash
bash performance/dataset/prepare-performance-database.sh
```

기대 결과:

```text
Performance database preparation completed.
Performance dataset gate completed.
```

주의:

- `PERF_DB_NAME`은 `jobflow_perf`처럼 운영 DB와 분리된 database를 사용한다.
- `PERF_DB_NAME=jobflow`처럼 실제 앱 DB를 지정하면 안 된다.
- `RESET_PERF_DB=true`는 성능 DB를 의도적으로 재생성할 때만 사용한다.

## 4-2. Performance profile 기동

staging/performance 서버에서 k6 측정 전에는 기본 compose에 performance override를 함께 적용한다.

```bash
docker compose -f docker-compose.yml -f docker-compose.performance.yml build backend gateway elasticsearch
docker compose -f docker-compose.yml -f docker-compose.performance.yml up -d
```

기대 설정:

```text
SPRING_PROFILES_ACTIVE=local,performance
backend datasource=jobflow_perf
Elasticsearch alias=jobflow-jobs-performance
Elasticsearch index=jobflow-jobs-performance-v1
startup reindex=true
```

기동 상태 확인:

```bash
docker compose -f docker-compose.yml -f docker-compose.performance.yml ps
```

backend/gateway가 healthy가 될 때까지 기다린다.

## 4-3. Performance reindex 확인

performance profile로 backend가 기동되면 `jobflow_perf`의 fixture가 Elasticsearch performance index로 reindex되어야 한다.

```bash
docker compose -f docker-compose.yml -f docker-compose.performance.yml logs --tail=200 backend \
  | grep -Ei 'reindex|indexedCount|Application run failed|alias'
```

기대 결과:

```text
Job search reindex batch completed. indexedCount=500
Job search reindex batch completed. indexedCount=1000
Job search reindex completed. indexedCount=1000
```

alias 오류가 보이면 performance profile 또는 ES alias 설정이 잘못된 것이다.

대표 오류:

```text
alias [jobflow-jobs] has more than one write index
```

이 경우 확인할 값:

```bash
docker compose -f docker-compose.yml -f docker-compose.performance.yml config \
  | grep -E 'SPRING_PROFILES_ACTIVE|ELASTICSEARCH_JOBS_ALIAS|ELASTICSEARCH_JOBS_INDEX|SPRING_DATASOURCE_URL'
```

기대값:

```text
SPRING_PROFILES_ACTIVE: local,performance
ELASTICSEARCH_JOBS_ALIAS: jobflow-jobs-performance
ELASTICSEARCH_JOBS_INDEX: jobflow-jobs-performance-v1
SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/jobflow_perf...
```

## 5. Pre-k6 integrated smoke

k6를 실행하기 직전에는 개별 smoke를 직접 조합하지 않고 통합 preflight smoke를 먼저 실행한다.

```bash
BASE_URL=http://localhost:8081/api \
BACKEND_URL=http://localhost:8080 \
GATEWAY_URL=http://localhost:8081 \
PROMETHEUS_URL=http://localhost:9090 \
GRAFANA_URL=http://localhost:3001 \
ZIPKIN_URL=http://localhost:9411 \
ELASTICSEARCH_URL=http://localhost:9200 \
bash performance/deploy/staging-pre-k6-smoke.sh
```

성공 기준:

```text
Staging pre-k6 smoke completed.
```

포함 항목:

- staging configuration gate
- staging readiness smoke
- job list filter smoke
- search intent smoke
- actuator exposure smoke
- performance profile smoke

## 6. Staging readiness smoke

전체 준비 상태를 한 번에 확인한다.

```bash
BASE_URL=http://localhost:8081/api \
BACKEND_URL=http://localhost:8080 \
GATEWAY_URL=http://localhost:8081 \
PROMETHEUS_URL=http://localhost:9090 \
GRAFANA_URL=http://localhost:3001 \
ZIPKIN_URL=http://localhost:9411 \
ELASTICSEARCH_URL=http://localhost:9200 \
bash performance/deploy/staging-readiness-smoke.sh
```

성공 기준:

```text
Staging readiness smoke completed.
```

확인 항목:

- backend/gateway health `UP`
- Gateway API routing
- OpenAPI/Swagger UI
- Elasticsearch cluster health
- Prometheus backend/gateway target
- Grafana datasource
- Zipkin API

참고:

- 단일 노드 Elasticsearch는 replica shard 때문에 `yellow`가 정상일 수 있다.
- `red`는 배포 차단이다.

## 7. OpenAPI smoke

```bash
BASE_URL=http://localhost:8081/api \
bash performance/openapi/openapi-contract-smoke.sh
```

성공 기준:

```text
OpenAPI contract smoke completed.
```

## 8. Gateway smoke

```bash
BASE_URL=http://localhost:8081/api \
bash performance/gateway/gateway-smoke.sh
```

성공 기준:

```text
Gateway smoke completed.
```

## 9. Observability smoke

```bash
BACKEND_URL=http://localhost:8080 \
PROMETHEUS_URL=http://localhost:9090 \
GRAFANA_URL=http://localhost:3001 \
bash performance/observability/prometheus-grafana-smoke.sh
```

성공 기준:

```text
Observability stack smoke completed.
```

## 10. Zipkin smoke

```bash
BASE_URL=http://localhost:8081/api \
ZIPKIN_URL=http://localhost:9411 \
bash performance/observability/zipkin-tracing-smoke.sh
```

성공 기준:

```text
Zipkin tracing smoke completed.
```

## 11. Actuator exposure smoke

Prometheus scrape에 필요한 직접 actuator endpoint는 유지하되, Gateway API prefix로 backend actuator가 프록시 노출되지 않는지 확인한다.

```bash
BACKEND_URL=http://localhost:8080 \
GATEWAY_URL=http://localhost:8081 \
BASE_URL=http://localhost:8081/api \
bash performance/security/actuator-exposure-smoke.sh
```

성공 기준:

```text
Actuator exposure smoke completed.
```

확인 기준:

- `backend /actuator/health`: 200
- `backend /actuator/prometheus`: 200
- `gateway /actuator/health`: 200
- `gateway /actuator/prometheus`: 200
- `gateway /api/actuator/health`: 404
- `gateway /api/actuator/prometheus`: 404

Prometheus는 compose 내부 네트워크에서 backend/gateway actuator를 직접 scrape한다. 외부 API entrypoint인 `/api/actuator/**`는 Gateway route에서 먼저 차단한다.

## 12. 배포 전 gate

아래 항목이 모두 충족되어야 k6 측정으로 넘어간다.

- `spring.jpa.open-in-view=false`
- HikariCP pool size 명시
- graceful shutdown 활성화
- Docker Compose healthcheck 설정
- `depends_on.condition` 설정
- container memory limit 설정
- Spring Batch metadata table Flyway migration 존재
- Elasticsearch index 존재 또는 초기화 runbook 존재
- `OAUTH2_SUCCESS_REDIRECT_URI` staging 값 명시
- actuator 외부 노출 정책 확인
- Collector Prometheus 노출 설정 확인
- scheduler 기본 enabled 정책 확인

## 13. 장애 대응

### Gateway 503

확인:

```bash
docker compose ps
docker compose logs --tail=100 backend
docker compose logs --tail=100 gateway
```

주요 원인:

- backend가 아직 healthy가 아님
- backend boot 실패
- gateway `BACKEND_URL` 오설정

### OAuth redirect 실패

확인할 값:

- GitHub OAuth App callback URL
- `GITHUB_OAUTH_REDIRECT_URI`
- `OAUTH2_SUCCESS_REDIRECT_URI`
- `OAUTH2_FAILURE_REDIRECT_URI`

GitHub OAuth App callback URL과 `GITHUB_OAUTH_REDIRECT_URI`는 정확히 일치해야 한다.

### Prometheus target down

확인:

```bash
curl http://localhost:9090/api/v1/targets
curl http://localhost:8080/actuator/prometheus
curl http://localhost:8081/actuator/prometheus
```

### Elasticsearch red

확인:

```bash
curl http://localhost:9200/_cluster/health
curl http://localhost:9200/_cat/indices?v
```

`red`면 검색 품질/성능 측정을 진행하지 않는다.

## 14. k6 전 최종 확인

k6 실행 전에는 개별 smoke를 직접 조합하지 않고 pre-k6 통합 smoke를 실행한다.

```bash
BASE_URL=http://localhost:8081/api \
BACKEND_URL=http://localhost:8080 \
GATEWAY_URL=http://localhost:8081 \
PROMETHEUS_URL=http://localhost:9090 \
GRAFANA_URL=http://localhost:3001 \
ZIPKIN_URL=http://localhost:9411 \
ELASTICSEARCH_URL=http://localhost:9200 \
bash performance/deploy/staging-pre-k6-smoke.sh
```

통합 smoke가 통과한 뒤 k6 Round 1로 넘어간다.
