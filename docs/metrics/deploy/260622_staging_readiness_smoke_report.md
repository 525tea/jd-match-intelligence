# Staging Readiness Smoke Report

- Date: 2026-06-22
- Scope: staging/performance deployment readiness smoke
- Entry point: Gateway `http://localhost:8081/api`
- Script: `performance/deploy/staging-readiness-smoke.sh`

## Purpose

k6 성능 측정 전, 로컬이 아닌 production-like staging/performance 환경에서 최소 운영 준비 상태를 한 번에 확인하기 위한 smoke 결과다.

이 smoke는 API 기능 검증이 아니라 배포 직후 즉시 터질 수 있는 운영 의존성을 확인한다.

- backend/gateway health
- Gateway routing
- OpenAPI/Swagger UI
- Elasticsearch cluster/index readiness
- Prometheus scrape target
- Grafana datasource
- Zipkin API

## Command

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

## Result Summary

| Check | Result | Notes |
|---|---:|---|
| Backend health | UP | `/actuator/health` |
| Gateway health | UP | `/actuator/health` |
| Unauthorized boundary | 401 | `/auth/me` without token |
| Job search routing | 200 | `/jobs/search?keyword=backend` |
| OpenAPI title | JobFlow API | `/v3/api-docs` |
| OpenAPI path count | 34 | Contract exposed through Gateway |
| Bearer auth schema | true | JWT security scheme present |
| Swagger UI | 200 | `/swagger-ui/index.html` |
| Elasticsearch health | yellow | Single-node compose environment |
| Job-related ES index count | 2 | Search indices exist |
| Prometheus readiness | true | `/-/ready` |
| Prometheus backend target | up | `jobflow-backend` |
| Prometheus gateway target | up | `jobflow-gateway` |
| Grafana DB | ok | `/api/health` |
| Grafana Prometheus datasource | true | provisioned datasource |
| Zipkin API | 200 | `/api/v2/services` |
| Zipkin service count | 1 | trace API reachable |

## Raw Smoke Output

```text
BASE_URL=http://localhost:8081/api
BACKEND_URL=http://localhost:8080
GATEWAY_URL=http://localhost:8081
PROMETHEUS_URL=http://localhost:9090
GRAFANA_URL=http://localhost:3001
ZIPKIN_URL=http://localhost:9411
ELASTICSEARCH_URL=http://localhost:9200
JOB_SEARCH_KEYWORD=backend

### Health endpoints
backend_health=UP
gateway_health=UP

### Gateway API routing
auth_me_without_token_status=401
jobs_search_status=200
jobs_search_success=true

### OpenAPI
openapi_title=JobFlow API
openapi_version=3.1.0
openapi_path_count=34
has_bearer_auth=true
swagger_ui_status=200

### Elasticsearch
elasticsearch_status=yellow
job_related_index_count=2

### Prometheus
prometheus_ready=true
prometheus_backend_target=up
prometheus_gateway_target=up

### Grafana
grafana_database=ok
grafana_prometheus_datasource=true

### Zipkin
zipkin_status=200
zipkin_service_count=1

### Staging Readiness Smoke Summary
backend_health=UP
gateway_health=UP
auth_me_without_token_status=401
jobs_search_status=200
openapi_path_count=34
elasticsearch_status=yellow
job_related_index_count=2
prometheus_backend_target=up
prometheus_gateway_target=up
grafana_database=ok
grafana_prometheus_datasource=true
zipkin_service_count=1

Staging readiness smoke completed.
```

## Interpretation

Staging/performance 배포 전 gate의 핵심 의존성은 통과했다.

Elasticsearch `yellow`는 현재 단일 노드 Docker Compose 환경에서 replica shard가 할당되지 않아 발생할 수 있는 상태다. `red`가 아니며, 검색 API와 job-related index가 존재하므로 현재 staging smoke 기준에서는 통과로 본다.

Prometheus는 backend/gateway target을 모두 `up`으로 scrape하고 있으며, Grafana는 Prometheus datasource provisioning을 정상 인식했다. 따라서 k6 실행 전 HTTP latency/error rate와 JVM/container 지표를 대시보드에서 확인할 수 있는 상태다.

## Follow-up

- 실제 원격 staging 서버에서 동일 smoke를 재실행해 local Mac 결과와 분리한다.
- Collector service를 Docker Compose에 포함하는 시점에 Prometheus collector target을 추가한다.
- 외부 공개 환경에서는 `/actuator/**`, Grafana, Prometheus, Zipkin 노출 범위를 방화벽 또는 reverse proxy 정책으로 제한한다.
