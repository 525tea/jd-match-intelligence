# Zipkin Tracing Smoke Report

## 목적

Prometheus/Grafana가 API latency, error rate, cache hit rate 같은 수치 관측을 담당한다면, Zipkin은 느린 요청의 내부 흐름을 추적한다.

이번 smoke는 backend 요청이 Zipkin에 trace로 기록되는지 확인한다.

## 실행 환경

- Backend: `http://127.0.0.1:8080`
- Zipkin: `http://127.0.0.1:9411`
- Service name: `jobflow`
- Sampling probability: `1.0`

## 사전 조건

```bash
docker compose up -d --build zipkin backend
```

## 실행 명령

```bash
bash performance/observability/zipkin-tracing-smoke.sh
```

## 확인 절차

1. backend에 `/actuator/health`, `/trends/skills`, `/jobs/search` 요청을 보낸다.
2. Zipkin reporter 비동기 flush를 위해 잠시 대기한다.
3. `GET /api/v2/services`에서 `jobflow` service가 등록됐는지 확인한다.
4. `GET /api/v2/traces?serviceName=jobflow`에서 trace가 조회되는지 확인한다.
5. Zipkin UI에서 `Service Name = jobflow` 조건으로 `RUN QUERY`를 눌러 trace를 확인한다.

## 결과

```text
services=["jobflow"]
trace_count=27
first_trace_id=842264d2b936c63d
span_name_count=27
```

## 판정

PASS

## 해석

- `services=["jobflow"]`가 나온 것은 backend span이 Zipkin에 export됐다는 뜻이다.
- Zipkin UI는 자동으로 trace 목록을 띄우지 않는다.
- UI에서는 `Service Name = jobflow` 조건을 추가하고 `RUN QUERY`를 눌러야 trace가 보인다.

## Prometheus/Grafana와 역할 구분

- Prometheus/Grafana: 얼마나 느린지, 에러율이 얼마인지, cache hit rate가 어떤지 수치로 본다.
- Zipkin: 느린 요청이 어떤 내부 구간에서 시간을 썼는지 trace로 추적한다.

## 리스크

- local smoke는 sampling probability를 `1.0`으로 둔다. 운영에서는 trace 저장량을 줄이기 위해 sampling 비율을 낮춰야 한다.
- Zipkin reporter는 비동기 전송이므로 요청 직후 바로 조회하면 trace가 아직 보이지 않을 수 있다.
- UI에서는 검색 조건 없이 새로고침만 하면 trace가 보이지 않는다.
