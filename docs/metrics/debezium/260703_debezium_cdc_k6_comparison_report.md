# Debezium CDC k6 Comparison Report

## 목적

Debezium CDC 전환 후 Outbox 이벤트 발행 책임을 app-level relay에서 MySQL binlog 기반 CDC로 이전했을 때, API 부하와 대량 Outbox 이벤트 처리에 어떤 차이가 있는지 비교한다.

이번 report는 단건 connector smoke가 아니라, 같은 부하 조건에서 app relay baseline과 Debezium CDC after를 비교한 결과다.

## 측정 조건

| 항목 | 값 |
|---|---|
| 환경 | AWS EC2 staging/performance stack |
| load generator | k6 runner |
| API path | private gateway path |
| VU | 500 |
| duration | 5분 |
| 이벤트 부하 | Outbox 10,000건 seed |
| Kafka topic | `email.send` |
| app relay baseline | backend app-level Outbox Relay enabled |
| Debezium after | backend app-level Outbox Relay disabled, Debezium CDC enabled |
| 성공 기준 | HTTP error 0%, processed event 10,000, final Kafka lag 0 |

## 측정 전 보정

초기 측정에서는 비교를 오염시키는 변수가 있었다.

| 문제 | 보정 |
|---|---|
| Gateway circuit breaker가 500VU 중 open되어 fallback 응답 발생 | performance profile에서 gateway circuit breaker를 끄고 비교 |
| Debezium connector 재등록 시 기존 `outbox_events` row가 snapshot/backfill되어 lag 오염 | connector `snapshot.mode=no_data`로 고정 |
| public endpoint 경로 timeout | 최종 비교를 private gateway path로 통일 |

이 보정은 결과를 좋게 보이게 만들기 위한 튜닝이 아니라, Debezium과 app relay의 책임 차이만 비교하기 위한 측정 조건 통제다.

## 결과 요약

| 항목 | App relay baseline | Debezium CDC after | 변화 |
|---|---:|---:|---:|
| HTTP error rate | 0.00% | 0.00% | 동일 |
| checks | 100.00% | 100.00% | 동일 |
| requests | 327,239 | 349,337 | +22,098 |
| throughput | 985.97 req/s | 1053.46 req/s | +6.8% |
| overall p95 | 175.60ms | 128.94ms | -26.6% |
| overall p99 | 341.79ms | 248.48ms | -27.3% |
| max | 8.35s | 1.51s | -81.9% |
| processed events | 10,000 | 10,000 | 동일 |
| final Kafka lag | 0 | 0 | 동일 |

## endpoint별 latency

| endpoint | App relay p95 | Debezium p95 | App relay p99 | Debezium p99 |
|---|---:|---:|---:|---:|
| overall | 175.60ms | 128.94ms | 341.79ms | 248.48ms |
| gap analysis | 176.73ms | 130.83ms | 334.19ms | 255.51ms |
| jobs search | 175.08ms | 125.99ms | 401.26ms | 234.13ms |
| recommendations jobs | 175.20ms | 129.96ms | 315.99ms | 255.13ms |

## 이벤트 처리 결과

| 항목 | App relay baseline | Debezium CDC after |
|---|---:|---:|
| processed_count | 10,000 | 10,000 |
| final lag | 0 | 0 |
| outbox PENDING | 0 | 10,000 |
| outbox PUBLISHED | 10,000 | 0 |
| outbox FAILED | 0 | 0 |

Debezium CDC 경로에서는 app relay처럼 원본 `outbox_events` row를 `PUBLISHED`로 변경하지 않는다. 따라서 Debezium 경로의 성공 기준은 outbox status가 아니라 Kafka consumer의 processed count와 final lag다.

## Lag 해석

Debezium CDC after에서는 seed 직후 Kafka lag가 크게 튀었다. 최종 측정에서 `email.send` lag는 약 9,974까지 누적된 뒤 final lag 0으로 drain됐다.

이는 Debezium이 binlog 기반으로 outbox insert를 빠르게 Kafka에 발행하고, consumer가 뒤따라 처리하는 구조이기 때문이다. 반대로 app relay baseline은 Kafka lag spike가 낮게 보였지만, 이는 app relay가 더 천천히 publish해서 Kafka backlog가 낮게 나타나는 면도 있다.

따라서 lag peak만으로 우열을 판단하지 않고, API p95/p99, processed count, final lag를 함께 본다.

## 결론

Debezium CDC 전환 후 같은 500VU + Outbox 10,000건 조건에서 API p95/p99/max latency가 낮아졌다.

다만 이 결과를 "Debezium이 consumer 처리 속도를 높였다"라고 해석하지 않는다. 정확한 해석은 다음과 같다.

- 기존 app relay는 backend JVM 안에서 outbox polling, Kafka publish, status update를 수행했다.
- Debezium CDC는 commit된 outbox row를 MySQL binlog에서 읽어 Kafka로 전달하므로 producer-side relay 책임을 app process 밖으로 이전한다.
- 이 책임 분리로 API 요청 처리와 이벤트 발행 작업의 간섭이 줄었고, 같은 부하 조건에서 API tail latency가 낮아졌다.

따라서 이번 작업의 성과는 CDC 도입 자체가 아니라, app-level relay 책임을 인프라 CDC 경로로 분리하고 그 결과를 같은 조건의 k6/Grafana/Kafka lag 지표로 검증한 것이다.

## 산출물

Raw 산출물은 로컬 `artifacts/`에 보관한다.

| 유형 | 위치 |
|---|---|
| k6 JSON / Kafka CLI / Prometheus snapshot | `artifacts/debezium/260702_debezium_k6_comparison/` |
| Grafana capture | `artifacts/grafana/260702_debezium_k6_comparison/` |
| 작업일지 | `docs/worklog/260703_w9-6_worklog.md` |
