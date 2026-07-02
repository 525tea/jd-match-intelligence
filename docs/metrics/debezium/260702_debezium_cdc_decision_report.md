# Debezium CDC Decision Report

작성일: 2026-07-02

## 목적

Kafka 기반 이벤트 처리 검증 이후, 현재 Outbox Relay가 맡고 있는 DB polling과 Kafka publishing 책임을 Debezium CDC로 이전할지 판단한다.

## 현재 Kafka 기준선

JobFlow는 현재 Outbox Pattern과 Kafka consumer 기반 이벤트 처리 구조를 사용한다.

```text
Application transaction
  -> outbox_events insert

Outbox Relay
  -> outbox_events polling
  -> Kafka publish
  -> mark PUBLISHED / FAILED

Kafka consumer
  -> side effect
  -> idempotency / DLQ
```

Kafka consumer latency/lag/recovery 검증에서 확보한 기준선은 다음과 같다.

| 시나리오 | 결과 | 해석 |
|---|---|---|
| API-only 500VU 5분 | 443,939 requests, 1,474.38 req/s, error 0%, p95 7.51ms, p99 59.32ms | API 기준선 |
| API 500VU + Outbox 10,000건 end-to-end | 439,466 requests, 1,459.33 req/s, error 0%, p95 8.48ms, p99 137.65ms, processed 10,000, final lag 0 | Outbox -> Kafka -> consumer end-to-end 처리 완료 |
| Consumer down/recovery | CLI lag 10,000, Prometheus lag 약 9,921, consumer 재기동 후 final lag 0, processed 10,000 | consumer 중단 시 backlog 관측 및 recovery 확인 |
| DLQ poison/retry | PENDING -> RETRIED|1, poison payload retry 후 재격리 | DLQ 저장/조회/retry-by-id 검증 |
| Idempotency replay | processed_count 1, duplicate skip 1 | 동일 event id side effect 1회 제한 |
| Partition key/order | same key 5건, partition_count 1, sequence 1,2,3,4,5 | key 단위 partition ordering 확인 |

주의할 점:

- API-only p95 7.51ms 대비 Outbox 10,000건 end-to-end p95 8.48ms는 약 13% 증가다.
- 따라서 “p95 변화 5% 이내”라고 주장하지 않는다.
- Kafka 성과는 API latency가 완전히 동일하다는 것이 아니라, 동시 API 부하 중에도 이벤트 유실 없이 처리하고 consumer lag, DLQ, retry, idempotency, key ordering을 관측/검증했다는 점이다.

## 남은 문제

Kafka consumer와 실패 처리 기준은 검증했지만, producer side에서는 backend application이 여전히 다음 책임을 가진다.

- `outbox_events`를 주기적으로 polling한다.
- publish 대상 row를 claim한다.
- Kafka publish를 직접 수행한다.
- publish 결과에 따라 `PUBLISHED` 또는 `FAILED` 상태를 갱신한다.
- API server JVM 안에서 relay scheduler lifecycle을 함께 운영한다.

이 구조는 동작하지만, 이벤트 발행 책임이 application process 안에 남아 있다. Debezium CDC를 도입하면 commit된 outbox row 변경을 MySQL binlog 기반으로 Kafka에 전달할 수 있다.

## 결정

다음 단계에서 Debezium MySQL Connector를 도입해 app-level Outbox Relay polling/publishing 책임을 CDC로 이전한다.

목표 구조:

```text
Application transaction
  -> outbox_events insert

MySQL binlog
  -> Debezium MySQL Connector
  -> Kafka topic routing

Kafka consumer
  -> existing envelope parsing
  -> side effect
  -> idempotency / DLQ
```

핵심 결정:

- Outbox Pattern은 유지한다.
- Kafka consumer contract와 side effect 로직은 유지한다.
- Debezium은 API latency 개선보다 producer-side relay 책임 분리를 위해 도입한다.
- exactly-once라고 주장하지 않는다.
- Debezium 전환 이후에도 Kafka at-least-once delivery와 idempotent consumer를 전제로 둔다.
- 기존 Outbox Relay는 즉시 삭제보다 profile/property로 비활성화 가능한 rollback 경계를 우선 검토한다.

## 대안 비교

| 대안 | 장점 | 한계 |
|---|---|---|
| 현재 Outbox Relay 유지 | 이미 구현되어 있고 batch/retry/lock 제어가 쉽다 | application JVM이 polling/publishing 책임을 계속 가진다 |
| 도메인 transaction 안에서 Kafka 직접 publish | 구조가 단순해 보인다 | DB commit과 Kafka publish를 원자적으로 묶기 어렵고 이벤트 유실/phantom event 위험이 있다 |
| Debezium CDC 도입 | DB commit 이후 binlog 기반으로 Kafka에 전달하고 relay 책임을 application 밖으로 옮긴다 | connector/binlog/topic routing/offset 운영이 추가된다 |

## 검증 기준

Debezium 구현 작업에서는 다음을 확인한다.

| 검증 항목 | 완료 기준 |
|---|---|
| Connector readiness | connector status RUNNING, task status RUNNING |
| MySQL binlog | outbox row 변경이 connector에 감지됨 |
| Outbox insert -> Kafka publish | Outbox Relay를 끈 상태에서도 Kafka topic에 event가 발행됨 |
| Topic routing | 기존 consumer가 읽는 topic과 호환됨 |
| Message key | 기존 aggregate key 전략과 호환됨 |
| Envelope compatibility | consumer parser가 기존 계약으로 message를 처리함 |
| Consumer code unchanged | consumer side effect business logic 수정 없음 |
| Backend restart | backend 재시작 중 outbox event 유실 0건 |
| Connector recovery | connector stop 중 쌓인 event가 restart 후 drain됨 |
| Duplicate delivery | idempotent consumer로 side effect 1회 유지 |
| Final lag | 최종 consumer lag 0 |

## 측정 기준선

Debezium 전환 전후 비교는 Kafka consumer latency/lag/recovery 검증 수치를 기준으로 한다.

| 지표 | Kafka 기준선 |
|---|---|
| API 500VU + Outbox 10,000건 p95 | 8.48ms |
| API 500VU + Outbox 10,000건 p99 | 137.65ms |
| API error rate | 0% |
| processed event | 10,000 |
| final consumer lag | 0 |
| duplicate replay | side effect 1회 |

## 결론

Kafka 단계는 publish 실패를 Outbox로 추적하고, consumer 실패를 DLQ로 격리하며, duplicate replay를 idempotent consumer로 방어하고, consumer lag를 Grafana/CLI로 관측하는 기준까지 완료했다.

다음 개선 지점은 consumer가 아니라 producer side의 app-level relay 책임이다. Debezium CDC는 이 책임을 MySQL binlog 기반 CDC로 이전하기 위한 선택이다.

따라서 다음 작업은 Debezium MySQL Connector 설정과 전환 전후 검증이다. 성공 기준은 consumer 코드 수정 없이 Outbox insert가 Kafka publish로 이어지고, backend/connector 장애 시에도 이벤트 유실 없이 final lag 0으로 회복되는 것이다.
