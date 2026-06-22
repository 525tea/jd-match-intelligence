# Staging Config Gate Report

- Date: 2026-06-22
- Scope: staging/performance deployment configuration gate
- Script: `performance/deploy/staging-config-gate.sh`

## Purpose

Staging/performance 서버에 배포하기 전, 애플리케이션이 실행 중인지와 별개로 repository 설정 자체가 운영 준비 조건을 만족하는지 확인한다.

이 gate는 runtime smoke와 역할이 다르다.

- runtime smoke: 실제 떠 있는 서비스의 응답과 관측 가능성 확인
- config gate: 배포 전 파일 설정, migration, env template, Docker Compose 조건 확인

## Command

```bash
bash performance/deploy/staging-config-gate.sh
```

## Result Summary

| Gate | Result | Notes |
|---|---:|---|
| Required files | ok | app config, compose, env template, runbook, migration |
| Backend runtime settings | ok | graceful shutdown, open-in-view false, HikariCP, Batch schema policy |
| Collector runtime settings | ok | graceful shutdown, Prometheus endpoint, HikariCP |
| Gateway runtime settings | ok | graceful shutdown, Prometheus endpoint |
| Spring Batch metadata migration | ok | `BATCH_JOB_INSTANCE`, `BATCH_STEP_EXECUTION` |
| Staging env template | ok | secret placeholders, OAuth redirect, scheduler defaults |
| Actuator exposure artifacts | ok | smoke, report, runbook linkage |
| Docker Compose config | ok | memory limits, healthchecks, depends_on conditions, JVM options |

## Raw Output

```text
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
```

## Checked Items

### Backend

- `server.shutdown=graceful`
- `spring.lifecycle.timeout-per-shutdown-phase`
- `spring.jpa.open-in-view=false`
- HikariCP pool size externalization
- `spring.batch.jdbc.initialize-schema=never`

### Collector

- graceful shutdown
- shutdown phase timeout
- `/actuator/prometheus` exposure
- HikariCP pool size externalization

### Gateway

- graceful shutdown
- shutdown phase timeout
- `/actuator/prometheus` exposure

### Actuator Exposure

- actuator exposure smoke script exists
- actuator exposure report exists
- staging runbook includes actuator exposure smoke
- smoke verifies `/api/actuator/health` proxy boundary
- smoke verifies `/api/actuator/prometheus` proxy boundary

### Docker Compose

- memory limits for MySQL, Redis, Elasticsearch, backend, gateway, Prometheus, Grafana, Zipkin
- backend/gateway healthcheck
- backend waits for healthy MySQL, Redis, Elasticsearch
- gateway waits for healthy backend and Redis
- Prometheus waits for healthy backend and gateway
- backend/gateway container-aware JVM options
- Elasticsearch JVM heap options

## Interpretation

Staging/performance 배포 전 설정 gate는 통과했다.

이 결과는 k6 Round 1 전에 다음 운영 리스크를 닫았다는 의미다.

- JPA open-in-view 기본값으로 인한 DB connection 점유
- HikariCP 기본 pool size로 인한 부하 테스트 오염
- Spring Batch metadata table 누락
- 컨테이너 memory limit 부재로 인한 staging OOM
- MySQL/Redis/Elasticsearch 초기화 전 backend 기동
- backend 준비 전 gateway/Prometheus 기동
- OAuth redirect/env 값 누락
- Gateway API prefix를 통한 backend actuator 외부 노출
- scheduler가 staging 첫 기동 직후 의도치 않게 실행되는 문제

## Follow-up

- 원격 staging 서버에서 동일 gate를 재실행한다.
- Collector service를 Docker Compose에 포함하는 시점에 Prometheus scrape target도 gate에 추가한다.
- 외부 노출 환경에서는 actuator, Prometheus, Grafana, Zipkin 접근 제어를 별도 reverse proxy/firewall gate로 분리한다.
