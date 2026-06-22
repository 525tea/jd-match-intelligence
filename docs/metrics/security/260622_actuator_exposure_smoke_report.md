# Actuator Exposure Smoke Report

- Date: 2026-06-22
- Scope: Gateway-proxied actuator exposure boundary
- Script: `performance/security/actuator-exposure-smoke.sh`

## Purpose

Prometheus 관측성은 유지하되, Gateway 외부 경로를 통해 backend actuator endpoint가 노출되지 않는지 확인한다.

이 smoke는 다음 경계를 검증한다.

- backend/gateway actuator endpoint는 직접 접근 가능해야 한다.
- Prometheus는 compose/internal network에서 `/actuator/prometheus`를 scrape할 수 있어야 한다.
- 외부 API entrypoint인 `/api/actuator/**`는 backend actuator로 프록시되면 안 된다.

## Command

```bash
BACKEND_URL=http://localhost:8080 \
GATEWAY_URL=http://localhost:8081 \
BASE_URL=http://localhost:8081/api \
bash performance/security/actuator-exposure-smoke.sh
```

## Result Summary

| Check | Expected | Actual | Result |
|---|---:|---:|---|
| Backend direct `/actuator/health` | 200 | 200 | pass |
| Backend direct `/actuator/prometheus` | 200 | 200 | pass |
| Gateway direct `/actuator/health` | 200 | 200 | pass |
| Gateway direct `/actuator/prometheus` | 200 | 200 | pass |
| Gateway-proxied `/api/actuator/health` | 404 | 404 | pass |
| Gateway-proxied `/api/actuator/prometheus` | 404 | 404 | pass |

## Raw Output

```text
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
```

## Interpretation

Actuator 외부 노출 경계는 의도대로 동작한다.

Prometheus scrape에 필요한 직접 actuator endpoint는 열려 있고, Gateway API prefix를 통한 backend actuator proxy 경로는 `404`로 차단된다.

이 구조는 다음 운영 요구를 동시에 만족한다.

- 관측성 유지: Prometheus가 backend/gateway metrics를 수집할 수 있다.
- 외부 노출 축소: API Gateway 경유 `/api/actuator/**` 접근은 막는다.
- 배포 안전성: Gateway route 우선순위로 backend actuator proxy를 차단한다.

## Interview Note

Prometheus는 내부 네트워크에서 backend/gateway actuator를 직접 scrape하게 두고, 외부 API entrypoint인 `/api/actuator/**`는 Gateway route에서 먼저 차단했다.

즉, 메트릭 수집은 유지하면서 외부 사용자가 Gateway를 통해 backend actuator metric을 조회하는 경로는 닫았다.
