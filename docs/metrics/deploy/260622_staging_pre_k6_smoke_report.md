# Staging Pre-k6 Smoke Report

- Date: 2026-06-22
- Scope: staging/performance pre-k6 integrated smoke
- Entry point: Gateway `http://localhost:8081/api`
- Script: `performance/deploy/staging-pre-k6-smoke.sh`

## Purpose

k6 부하 테스트를 실행하기 전, 배포 설정과 핵심 런타임 의존성이 모두 준비됐는지 한 번에 확인하기 위한 통합 preflight smoke 결과다.

이 smoke는 개별 기능 하나를 깊게 검증하는 테스트가 아니라, 성능 테스트 전에 깨지면 안 되는 운영 전제 조건을 묶어서 확인한다.

- staging configuration gate
- staging runtime readiness
- job list filter API
- search intent ranking smoke
- actuator exposure boundary

## Command

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

## Result Summary

| Step | Result | Notes |
|---|---:|---|
| Staging config gate | ok | runtime settings, env template, compose, actuator artifacts |
| Staging readiness smoke | ok | health, routing, OpenAPI, ES, Prometheus, Grafana, Zipkin |
| Job list filter smoke | ok | pagination, role/status/location/remote filters, validation |
| Search intent smoke | ok | backend/frontend/data intent ranking |
| Actuator exposure smoke | ok | direct actuator allowed, Gateway-proxied backend actuator blocked |

## Key Runtime Results

| Check | Result |
|---|---:|
| Backend health | UP |
| Gateway health | UP |
| Unauthorized `/auth/me` | 401 |
| Job search | 200 |
| OpenAPI path count | 34 |
| Swagger UI | 200 |
| Elasticsearch status | yellow |
| Job-related ES index count | 2 |
| Prometheus backend target | up |
| Prometheus gateway target | up |
| Grafana DB | ok |
| Grafana Prometheus datasource | true |
| Zipkin service count | 1 |
| Gateway-proxied backend actuator health | 404 |
| Gateway-proxied backend actuator prometheus | 404 |

## Raw Smoke Output

```text
BASE_URL=http://localhost:8081/api
BACKEND_URL=http://localhost:8080
GATEWAY_URL=http://localhost:8081
PROMETHEUS_URL=http://localhost:9090
GRAFANA_URL=http://localhost:3001
ZIPKIN_URL=http://localhost:9411
ELASTICSEARCH_URL=http://localhost:9200

================================================================================
### Staging config gate
================================================================================
ROOT_DIR=/Users/iyejin/dev/jobflow

### Required files
required_files=ok

### Backend runtime settings
backend_runtime_settings=ok

### Collector runtime settings
collector_runtime_settings=ok

### Gateway runtime settings
gateway_runtime_settings=ok

### Batch migration
spring_batch_metadata_migration=ok

### Env template
staging_env_template=ok

### Actuator exposure artifacts
actuator_exposure_artifacts=ok

### Docker Compose config
docker_compose_config=ok

### Staging Config Gate Summary
required_files=ok
backend_runtime_settings=ok
collector_runtime_settings=ok
gateway_runtime_settings=ok
spring_batch_metadata_migration=ok
staging_env_template=ok
actuator_exposure_artifacts=ok
docker_compose_config=ok

Staging config gate completed.

================================================================================
### Staging readiness smoke
================================================================================
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

================================================================================
### Job list filter smoke
================================================================================
BASE_URL=http://localhost:8081/api
PAGE=0
SIZE=5
PAGED_SIZE=1
ROLE=BACKEND
STATUS=OPEN
LOCATION_REGION=Seoul
REMOTE_TYPE=ONSITE

### GET /jobs pagination
jobs_page_status=200
jobs_page_count=1

### GET /jobs role/status filter
jobs_role_status_filter_status=200
jobs_role_status_filter_count=5

### GET /jobs location filter
jobs_location_filter_status=200
jobs_location_filter_count=5

### GET /jobs remote filter
jobs_remote_filter_status=200
jobs_remote_filter_count=5

### GET /jobs validation
jobs_invalid_size_status=400

### Job List Filter Smoke Summary
jobs_page_status=200
jobs_page_count=1
jobs_role_status_filter_status=200
jobs_role_status_filter_count=5
jobs_location_filter_status=200
jobs_location_filter_count=5
jobs_remote_filter_status=200
jobs_remote_filter_count=5
jobs_invalid_size_status=400

Job list filter smoke completed.

================================================================================
### Search intent smoke
================================================================================
BASE_URL=http://localhost:8081/api
LIMIT=5
MIN_EXPECTED_ROLE_HITS=1
MIN_EXPECTED_CAREER_HITS=1
MIN_EXPECTED_REGION_HITS=1

### Search Intent Smoke Summary
total_query_count=3
passed_query_count=3
limit=5

Search intent smoke completed.

================================================================================
### Actuator exposure smoke
================================================================================
BACKEND_URL=http://localhost:8080
GATEWAY_URL=http://localhost:8081
BASE_URL=http://localhost:8081/api

### Direct actuator endpoints
backend_health_status=200
backend_prometheus_status=200
gateway_health_status=200
gateway_prometheus_status=200

### Gateway-proxied backend actuator boundary
proxied_backend_health_status=404
proxied_backend_prometheus_status=404

### Actuator Exposure Smoke Summary
backend_health_status=200
backend_prometheus_status=200
gateway_health_status=200
gateway_prometheus_status=200
proxied_backend_health_status=404
proxied_backend_prometheus_status=404

Actuator exposure smoke completed.

### Staging Pre-k6 Smoke Summary
staging_config_gate=ok
staging_readiness_smoke=ok
job_list_filter_smoke=ok
search_intent_smoke=ok
actuator_exposure_smoke=ok

Staging pre-k6 smoke completed.
```

## Interpretation

Staging/performance 환경에서 k6 실행 전 필요한 설정 gate와 runtime smoke가 모두 통과했다.

이 결과는 성능 테스트 전에 다음 리스크를 먼저 닫았다는 의미다.

- 설정 파일 누락이나 staging env template 누락
- Spring Batch metadata migration 누락
- HikariCP/open-in-view/graceful shutdown 설정 누락
- Docker Compose healthcheck와 memory limit 누락
- Gateway routing, OpenAPI, Swagger UI 장애
- Elasticsearch index 준비 실패
- Prometheus/Grafana/Zipkin 관측성 준비 실패
- Gateway prefix를 통한 backend actuator 외부 노출
- 공고 목록 필터와 검색 의도 smoke 실패

Elasticsearch `yellow`는 단일 노드 Docker Compose 환경에서 replica shard가 배정되지 않아 발생 가능한 상태다. 검색 API와 job-related index가 존재하고 smoke가 통과했으므로 현재 pre-k6 gate에서는 통과로 본다.

## Follow-up

- 원격 staging 서버에서 동일 pre-k6 smoke를 재실행한다.
- k6 Round 1 실행 후 Prometheus/Grafana 지표와 함께 latency/error 결과를 별도 리포트로 남긴다.
- Collector가 Docker Compose runtime service에 포함되는 시점에 collector health/Prometheus target도 pre-k6 smoke에 추가한다.
