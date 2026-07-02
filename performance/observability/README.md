# Observability Failure Scenarios

이 디렉터리는 JobFlow의 장애 관측 시나리오를 재현하고 Grafana/Kibana 산출물을 수집하기 위한 스크립트를 제공한다.

검색, 캐시, 이벤트 발행, CDC, 보안 이벤트 파이프라인에서 발생할 수 있는 대표 장애를 의도적으로 주입하고, 장애 중 지표 변화와 복구 후 상태를 함께 검증한다.

검증 범위는 다음과 같다.

- Elasticsearch 장애 시 검색 요청이 MySQL FULLTEXT fallback 경로로 전환되는지 확인한다.
- Redis 장애 시 cache/rate-limit 의존 API가 graceful degradation 되는지 확인한다.
- Kafka consumer 중단 시 consumer lag가 누적되고, 재기동 후 0까지 drain 되는지 확인한다.
- Debezium connector/backend consumer 중단 시 CDC 기반 Outbox 이벤트가 유실 없이 복구되는지 확인한다.
- Gateway 보안 이벤트가 Kafka -> Logstash -> Elasticsearch -> Kibana 경로로 적재되고 eventType/status/path 기준으로 조회되는지 확인한다.
- Elasticsearch fallback, Outbox 상태, Kafka consumer lag를 Prometheus custom metric과 Grafana panel로 관측한다.

## 전체 실행

staging/performance 서버에서 실행한다.

```bash
REQUIRED_PORTS="" \
bash performance/deploy/staging-performance-up.sh

SCENARIO_MODE=all \
RUN_ID=failure-observability-$(date +%Y%m%d%H%M%S) \
bash performance/observability/run-failure-observability-scenario.sh
```

기본 산출물 위치:

```text
artifacts/observability/YYMMDD_failure_observability/
```

## 개별 실행

장애별로 따로 실행해 검증한다.

```bash
SCENARIO_MODE=security-events \
bash performance/observability/run-failure-observability-scenario.sh

SCENARIO_MODE=elasticsearch-fallback \
bash performance/observability/run-failure-observability-scenario.sh

SCENARIO_MODE=redis-degradation \
bash performance/observability/run-failure-observability-scenario.sh

SCENARIO_MODE=kafka-consumer-recovery \
KAFKA_RECOVERY_EVENT_COUNT=10000 \
bash performance/observability/run-failure-observability-scenario.sh

SCENARIO_MODE=debezium-recovery \
DEBEZIUM_RECOVERY_MODE=all \
DEBEZIUM_RECOVERY_EVENT_COUNT=10000 \
bash performance/observability/run-failure-observability-scenario.sh
```

### Grafana

기본 URL:

```text
http://3.39.242.44:3001/d/jobflow-backend/jobflow-backend-observability?orgId=1&refresh=5s&from=now-15m&to=now
```

관측 패널:

- HTTP request rate
- P95/P99 latency
- Error rate
- Cache hit rate
- HikariCP connections
- JVM heap/thread
- Kafka Consumer Lag
- Search Fallback Rate
- Outbox Event Status

이번 작업에서 추가한 custom metric:

| Metric | 의미 |
|---|---|
| `jobflow_search_fallback_total{source="elasticsearch",target="mysql_fulltext",reason="elasticsearch_error"}` | Elasticsearch 검색 실패 후 MySQL FULLTEXT fallback으로 처리한 누적 횟수 |
| `jobflow_outbox_events{status="pending"}` | 현재 PENDING Outbox event 수 |
| `jobflow_outbox_events{status="failed"}` | 현재 FAILED Outbox event 수 |
| `jobflow_outbox_events{status="published"}` | 현재 PUBLISHED Outbox event 수 |

산출물 파일명:

```text
260703_<capture-name>.png
```

### Kibana

기본 URL:

```text
http://3.39.242.44:5601
```

Kibana에서는 `jobflow-security-events` index 기준으로 다음 필드를 본다.

- `eventType`
- `status`
- `path`
- `requestId`
- `clientIp`
- `userAgent`

스크립트가 출력한 `requestId prefix`로 필터링해 해당 run의 이벤트만 볼 수 있다.

## 시나리오별 판정 기준

| 시나리오 | 정상 판정 |
|---|---|
| `security-events` | Elasticsearch `jobflow-security-events`에서 run id prefix 이벤트가 3건 이상 조회되고, `ABNORMAL_REQUEST`, `AUTH_FAILURE`가 집계된다. |
| `elasticsearch-fallback` | Elasticsearch 중단 중 `/jobs/search` 요청 결과를 기록하고, restore 후 `/jobs/search`가 200으로 회복된다. 기본값은 down 중에도 200 fallback을 요구한다. |
| `redis-degradation` | Redis 중단 중 `/jobs/search`, `/trends/skills` 요청 결과를 기록하고, restore 후 `/jobs/search`가 200으로 회복된다. 기본값은 down 중에도 200 graceful degradation을 요구한다. |
| `kafka-consumer-recovery` | consumer disabled 중 lag가 누적되고, consumer enabled 후 final lag 0 및 processed count 일치를 확인한다. |
| `debezium-recovery` | connector pause/resume 또는 backend consumer restart 후 processed count와 final lag 0을 확인한다. |

스크립트는 각 장애 시점에 Prometheus query 결과를 `artifacts/observability/` 아래 JSON으로 저장하도록 했다. 

특히 `elasticsearch-fallback`은 `jobflow_search_fallback_total`, Kafka/Debezium recovery는 `jobflow_outbox_events`와 `kafka_consumergroup_lag`를 함께 남긴다.

## 실패를 허용하고 관측만 할 때

Elasticsearch 또는 Redis down 중 200을 강제하지 않고, 장애 양상만 캡처하려면 다음 값을 끈다.

```bash
REQUIRE_ES_FALLBACK_200=false \
SCENARIO_MODE=elasticsearch-fallback \
bash performance/observability/run-failure-observability-scenario.sh

REQUIRE_REDIS_DEGRADATION_200=false \
SCENARIO_MODE=redis-degradation \
bash performance/observability/run-failure-observability-scenario.sh
```

이 경우에도 restore 후 200 회복은 필수로 확인한다.

## 주의

- 이 스크립트는 Docker Compose service를 의도적으로 stop/restart한다.
- 중간 실패가 발생해도 trap으로 stop한 service를 복구하려고 시도한다.
- raw JSON/PNG는 `artifacts/` 아래 로컬 보관 대상이며 Git에 올리지 않는다.
- 공개 가능한 결과는 실행 후 `docs/metrics/observability/`의 Markdown report에 핵심 수치만 옮긴다.
