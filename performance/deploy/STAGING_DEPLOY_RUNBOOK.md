# JobFlow Staging/Performance Deploy Runbook

이 문서는 staging/performance 서버에 JobFlow를 올리고, k6 성능 측정 전 운영 준비 상태를 확인하는 절차다.

## 1. 배포 대상

우선순위:

1. OCI Always Free
2. OCI capacity 또는 ARM64 image 이슈 발생 시 AWS EC2 fallback

최종 k6 수치는 로컬 Mac이 아니라 staging/performance 서버 기준으로 남긴다.

## 2. 서버 준비

필수 도구:

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

## 13. k6 전 최종 확인

k6 실행 전 최소 확인:

```bash
BASE_URL=http://localhost:8081/api \
bash performance/deploy/staging-readiness-smoke.sh

BASE_URL=http://localhost:8081/api \
bash performance/job/job-list-filter-smoke.sh

BASE_URL=http://localhost:8081/api \
bash performance/elasticsearch/search-intent-smoke.sh

BACKEND_URL=http://localhost:8080 \
GATEWAY_URL=http://localhost:8081 \
BASE_URL=http://localhost:8081/api \
bash performance/security/actuator-exposure-smoke.sh
```

세 smoke가 모두 통과한 뒤 k6 Round 1로 넘어간다.
