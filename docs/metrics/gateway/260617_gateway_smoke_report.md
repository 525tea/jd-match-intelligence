# Gateway Smoke Report

작성일: 2026-06-17

## 목적

Spring Cloud Gateway가 API 진입점으로 동작하는지 확인했다.
검증 범위는 공개 API 라우팅, JWT pass-through, Redis 기반 rate limit, circuit breaker fallback, Prometheus 수집이다.

## 실행 환경

- Gateway URL: `http://127.0.0.1:8081`
- Backend service: `backend`
- Prometheus URL: `http://127.0.0.1:9090`
- Rate limit request count: `120`
- Rate limit policy: `100 req/min`

## 검증 결과

| 항목 | 결과 |
| --- | --- |
| Gateway health | 성공 |
| Public API routing | 성공 |
| JWT pass-through | 성공 |
| Redis fixed-window rate limit | 성공 |
| Circuit breaker fallback | 성공 |
| Backend recovery routing | 성공 |
| Prometheus gateway metric scrape | 성공 |

## Smoke Summary

```text
gateway_health_up=true
gateway_routing_success=true
rate_limit_request_count=120
rate_limit_429_count=20
fallback_status=503
backend_recovery_success=true
prometheus_gateway_metric_found=true
```

## 주요 확인 사항

### Public API Routing

`/api/jobs/search` 요청이 Gateway를 거쳐 Backend로 정상 전달됐다.
Gateway는 `/api/**` 경로에서 prefix를 제거한 뒤 backend API로 전달한다.

### JWT Pass-through

Gateway는 JWT를 직접 검증하지 않고 `Authorization` header를 Backend로 전달한다.
JWT 검증 책임은 기존 Backend security filter가 유지한다.

### Rate Limit

Redis fixed-window 방식으로 client key별 minute window 요청 수를 집계한다.
120회 연속 요청에서 100회 이후 요청이 `429 Too Many Requests`로 차단됐다.

### Circuit Breaker Fallback

Backend container를 중단한 뒤 Gateway 요청을 보냈을 때 `503 Service Unavailable` fallback 응답이 반환됐다.

```json
{
  "success": false,
  "error": {
    "code": "GATEWAY_BACKEND_UNAVAILABLE",
    "message": "백엔드 서비스를 일시적으로 사용할 수 없습니다."
  }
}
```

### Backend Recovery

Backend container를 다시 시작한 뒤 Gateway routing이 정상 복구됐다.

### Prometheus

Prometheus target에 `jobflow-gateway`가 등록되고 `health="up"` 상태로 수집됐다.
Gateway HTTP metric query도 결과를 반환했다.

## 운영 메모

- OAuth secret, provider token, JWT 값은 보고서에 기록하지 않았다.
- Docker Compose OAuth 검증은 로컬 `.env` 기반으로만 수행한다.
- Prometheus 설정 변경 후에는 Prometheus container restart가 필요하다.
