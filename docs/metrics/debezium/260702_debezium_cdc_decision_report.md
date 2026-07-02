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

단건 CDC path는 `260702_debezium_outbox_cdc_smoke_report.md`에서 정리했고, 대량 부하 비교는 `260703_debezium_cdc_k6_comparison_report.md`에서 정리했다.

| 검증 항목 | 완료 기준 / 현재 상태                                                                       |
|---|-------------------------------------------------------------------------------------|
| Connector readiness | 완료. connector status RUNNING, task status RUNNING                                   |
| MySQL binlog | 완료. `outbox_events.id=162506` 변경 감지됨                                                |
| Outbox insert -> Kafka publish | 완료. Outbox Relay disabled 상태에서 Kafka `email.send` 발행                                |
| Topic routing | 완료. `outbox_events.topic` -> `email.send`                                           |
| Message key | smoke에서 aggregate id key 확인                                                         |
| Envelope/header compatibility | 완료. Debezium Event Router가 event id를 Kafka header `id`에 실어 parser adapter 보강        |
| Consumer business logic | 이메일 발송/검색 색인 side effect logic은 유지. Kafka listener/parser adapter는 header 수용을 위해 변경 |
| Backend restart | 별도 장애 시나리오로 유지. 관측/장애 검증에서 캡처 보강 예정                                      |
| Connector recovery | 별도 장애 시나리오로 유지. 관측/장애 검증에서 캡처 보강 예정                                     |
| Duplicate delivery | 기존 idempotent consumer 유지. Debezium header id 수용 후 동일 모델 유지                       |
| Final lag | 완료. Debezium CDC 대량 조건에서 processed 10,000, final lag 0 확인                         |

## 측정 기준선

Debezium 전환 전후 비교는 Kafka consumer latency/lag/recovery 검증 수치를 기준으로 시작했고, 이후 app relay baseline과 Debezium CDC after를 같은 조건으로 재측정했다.

| 지표 | App relay baseline | Debezium CDC after |
|---|---:|---:|
| API 500VU + Outbox 10,000건 p95 | 175.60ms | 128.94ms |
| API 500VU + Outbox 10,000건 p99 | 341.79ms | 248.48ms |
| max latency | 8.35s | 1.51s |
| throughput | 985.97 req/s | 1053.46 req/s |
| API error rate | 0% | 0% |
| processed event | 10,000 | 10,000 |
| final consumer lag | 0 | 0 |

## 결론

Kafka 단계는 publish 실패를 Outbox로 추적하고, consumer 실패를 DLQ로 격리하며, duplicate replay를 idempotent consumer로 방어하고, consumer lag를 Grafana/CLI로 관측하는 기준까지 완료했다.

다음 개선 지점은 consumer가 아니라 producer side의 app-level relay 책임이다. Debezium CDC는 이 책임을 MySQL binlog 기반 CDC로 이전하기 위한 선택이다.

Debezium MySQL Connector 설정과 단건 Outbox CDC smoke는 완료했다. relay disabled 상태에서 event id `162506`이 Kafka `email.send`로 발행됐고, Debezium header `id`를 backend consumer가 idempotency event id로 읽어 `processed_count=1`을 남겼다. 이 과정에서 “consumer 코드 수정 없음”은 실제 Debezium header contract 때문에 그대로 유지하지 않았다. 대신 side effect business logic과 idempotency 저장 모델은 유지하고, Kafka listener/parser adapter가 header `id`를 수용하도록 보강했다.

전환 전후 대량 비교도 완료했다. 같은 private gateway path, 500VU, 5분, Outbox 10,000건 조건에서 Debezium CDC after는 app relay baseline 대비 p95/p99/max latency가 낮았고, 두 경로 모두 error 0%, processed 10,000, final lag 0을 만족했다. Debezium 경로에서 원본 outbox row가 `PENDING`으로 남는 것은 실패가 아니며, 성공 기준은 Kafka consumer processed count와 final lag다.

다음 보강은 Debezium 자체의 성능 비교가 아니라 운영 장애 관측이다. connector stop/restart, backend restart, Grafana/Kibana 캡처, error spike/recovery는 별도 관측/장애 검증 작업에서 다룬다.
