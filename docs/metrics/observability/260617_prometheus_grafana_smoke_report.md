# Prometheus Grafana Smoke Report

## 목적

Prometheus와 Grafana 기반 백엔드 관측 스택이 로컬 Docker Compose 환경에서 재현 가능하게 동작하는지 확인한다.

이번 smoke는 다음 항목을 검증한다.

- Spring Boot backend의 `/actuator/prometheus` metric 노출
- Prometheus의 backend scrape target 상태
- Prometheus query API에서 HTTP/cache/JVM metric 수집 여부
- Grafana datasource provisioning
- Grafana dashboard JSON provisioning

## 측정 기준

| 항목 | 값 |
| --- | --- |
| 측정일 | `2026-06-17` |
| Backend URL | `http://127.0.0.1:8080` |
| Prometheus URL | `http://127.0.0.1:9090` |
| Grafana URL | `http://127.0.0.1:3001` |
| Grafana datasource | `Prometheus` |
| Grafana dashboard | `JobFlow Backend Observability` |
| Docker Compose services | `backend`, `prometheus`, `grafana` |
| Smoke script | `performance/observability/prometheus-grafana-smoke.sh` |

민감정보는 기록하지 않는다.

- Grafana admin password는 기록하지 않는다.
- 실제 사용자 token이나 OAuth provider token은 사용하지 않는다.
- Prometheus/Grafana smoke는 공개 가능한 로컬 관측 지표만 기록한다.

## 구성

| 구성 요소 | 역할 |
| --- | --- |
| Backend Actuator | `/actuator/prometheus` metric 노출 |
| Micrometer Prometheus Registry | Spring metric을 Prometheus format으로 변환 |
| Prometheus | backend metric scrape 및 query |
| Grafana Datasource Provisioning | Prometheus datasource 자동 등록 |
| Grafana Dashboard Provisioning | JobFlow backend dashboard 자동 등록 |

## Dashboard 패널

| 패널 | 목적 |
| --- | --- |
| HTTP Request Rate | URI/method/status 기준 요청량 확인 |
| P95 / P99 Latency | URI 기준 latency percentile 확인 |
| Error Rate | 4xx/5xx 비율 확인 |
| Cache Hit Rate | Spring Cache hit/miss 비율 확인 |
| JVM Memory Used | heap/non-heap memory 사용량 확인 |

## Smoke 결과

| Metric | 값 |
| --- | ---: |
| prometheus_ready | true |
| prometheus_target_up | true |
| http_metric_count | 3 |
| cache_metric_count | 27 |
| jvm_metric_count | 8 |
| grafana_health_ok | true |

Grafana provisioning 결과:

| 항목 | 값 |
| --- | --- |
| datasource uid | `Prometheus` |
| dashboard folder | `JobFlow` |
| dashboard uid | `jobflow-backend` |
| dashboard title | `JobFlow Backend Observability` |

## Prometheus target

| 항목 | 값 |
| --- | --- |
| job | `jobflow-backend` |
| scrape URL | `http://backend:8080/actuator/prometheus` |
| health | `up` |
| last error | 없음 |

## 실행 명령

사전 조건:

```bash
docker compose up -d --build backend prometheus grafana
```

Smoke 실행:

```bash
bash performance/observability/prometheus-grafana-smoke.sh
```

## 해석

Prometheus는 Docker Compose service name인 `backend:8080`으로 backend metric을 정상 scrape했다.

Grafana는 provisioning 설정을 통해 Prometheus datasource와 JobFlow backend dashboard를 자동으로 로드했다. 따라서 로컬 클론 후 Docker Compose로 관측 스택을 재현할 수 있다.

`P95/P99 latency`, `Error Rate`, `Cache Hit Rate` 패널은 부하나 cacheable API 호출이 충분히 발생해야 의미 있는 값이 채워진다. 이번 smoke에서는 Prometheus query API 기준으로 HTTP/cache/JVM metric이 수집되는 것을 확인했다.

## 결론

PASS.

Prometheus + Grafana 관측 스택은 로컬 Docker Compose 환경에서 재현 가능하게 동작한다.
