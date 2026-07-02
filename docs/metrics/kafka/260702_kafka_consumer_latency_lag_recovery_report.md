# Kafka Consumer Latency, Lag, Failure Recovery Report

작성일: 2026-07-02

## 목적

Kafka consumer lag 관측과 장애 복구 시나리오를 동일 staging/performance 환경에서 검증한다. 단순 topic smoke가 아니라 API 부하 중 이벤트 backlog가 발생했을 때 API error rate, p95/p99 latency, consumer lag spike/drain, DLQ retry, idempotent consumer, partition key ordering을 함께 확인한다.

## 환경

| 항목 | 값 |
|---|---|
| application host | staging-performance EC2 |
| load generator | k6-runner EC2 |
| application profile | performance |
| Kafka topics | `email.send`, `job.created`, `security.events`, DLQ topics |
| 주요 관측 | k6 JSON, Kafka CLI consumer group lag, Prometheus query, Grafana capture |
| raw artifacts | `artifacts/kafka/260702_kafka_consumer_latency_lag`, `artifacts/kafka/260702_kafka_consumer_failure_recovery`, `artifacts/kafka/260702_kafka_failure_recovery` |

## API 부하 결과

| 시나리오 | 요청 수 | throughput | error | p95 | p99 | 판단 |
|---|---:|---:|---:|---:|---:|---|
| API-only 500VU 5분 | 443,939 | 1,474.38 req/s | 0% | 7.51ms | 59.32ms | API 기준선 |
| API 500VU + Kafka direct burst 30,000건 | 422,075 | 1,398.38 req/s | 0% | 90.67ms | 431.99ms | lag spike/drain 관측성 검증 |
| API 500VU + Outbox 10,000건 end-to-end | 439,466 | 1,459.33 req/s | 0% | 8.48ms | 137.65ms | Outbox publish/consume 완료, final lag 0 |

API 500VU + Outbox 10,000건 end-to-end 조건에서 Outbox는 `10,000`건 `PUBLISHED`, consumer processed count는 `10,000`, failed count는 `0`, final lag는 `0`으로 확인됐다.

API-only p95 `7.51ms` 대비 Outbox 10,000건 end-to-end p95 `8.48ms`는 약 13% 증가다. 따라서 “p95 5% 이내” 목표 달성으로 표현하지 않는다. 대신 이 결과는 동시 API 부하 중에도 error `0%`, p95 `10ms` 미만, Outbox 10,000건 publish/consume 완료, final lag `0`을 확인한 수치로 해석한다.

## Consumer down/recovery

| 항목 | 결과 |
|---|---:|
| seeded outbox events | 10,000 |
| accumulated CLI lag | 10,000 |
| Prometheus lag snapshot | 약 9,921 |
| drain progression | 10000 -> 10000 -> 8500 -> 7000 -> 5500 -> 5000 -> 3550 -> 2650 -> 830 -> 0 |
| final lag | 0 |
| processed count | 10,000 |

consumer disabled 상태에서도 Outbox Relay가 Kafka publish를 완료했고, consumer group active member가 없는 동안 `email.send` lag가 누적됐다. consumer 재기동 후 lag는 0까지 drain됐고 전체 10,000건 처리가 확인됐다.

## DLQ poison/retry

| 항목 | 결과 |
|---|---|
| poison event | malformed `email.send` message |
| initial DLQ row | `PENDING` |
| detail API | success |
| retry API | success |
| retried row | `RETRIED|1` |
| source key 기준 DLQ row count after retry | 2 |

poison payload는 retry해도 원본 payload가 그대로이므로 다시 실패하고 새 DLQ row가 생긴다. 이는 payload 자동 수정이 아니라 저장된 DLQ envelope를 원본 topic으로 재발행할 수 있는지 검증한 결과다.

초기 실행에서 `email.send.dlq` topic이 없어 retry 후 재실패가 발생했다. 이후 주요 DLQ topic을 topic bootstrap/smoke 대상에 포함해 재실행했고 성공했다.

## Idempotency

| 항목 | 결과 |
|---|---:|
| replay event id | 8882956981 |
| processed_count | 1 |
| duplicate_skip_log_count | 1 |

동일 event id가 재전달되어도 `processed_kafka_events` 기반 idempotent consumer가 side effect를 1회로 제한하는 것을 확인했다.

## Partition key/order

| 항목 | 결과 |
|---|---:|
| matched_count | 5 |
| partition_count | 1 |
| sequence_list | 1,2,3,4,5 |

동일 key 메시지 5건이 같은 partition에 들어갔고 sequence가 유지됐다. 이는 global ordering 보장이 아니라 같은 key 기준 partition ordering 검증이다.

## 결론

- API-only 500VU 기준선은 p95 `7.51ms`, error `0%`다.
- API 500VU + Outbox 10,000건 end-to-end 조건은 p95 `8.48ms`, error `0%`, processed `10,000`, final lag `0`이다.
- consumer disabled 상태에서 lag `10,000` 누적 후 consumer 재기동으로 final lag `0`, processed `10,000`을 확인했다.
- DLQ 저장/상세/retry-by-id, idempotent replay 방어, partition key ordering을 smoke로 확인했다.

정확한 표현은 Kafka Transactions 기반 exactly-once가 아니라, Kafka at-least-once delivery 위에 DB unique constraint 기반 idempotent consumer를 둔 application-level effectively-once 구조다.
