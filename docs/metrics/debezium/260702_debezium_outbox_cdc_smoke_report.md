# Debezium Outbox CDC Smoke Report

작성일: 2026-07-02

## 목적

Debezium MySQL Connector가 `outbox_events` 변경을 MySQL binlog에서 읽어 Kafka topic으로 발행하고, 기존 backend consumer/idempotency 경로까지 연결되는지 확인한다.

이번 smoke는 대량 부하 전후 비교가 아니라 Debezium connector 설정과 최소 end-to-end CDC 경로 검증이다.

## 환경

| 항목 | 값 |
|---|---|
| 실행 환경 | AWS EC2 staging/performance Docker Compose stack |
| Database | MySQL performance DB |
| CDC | Debezium Connect + MySQL Connector |
| Kafka topic | `email.send` |
| Backend relay | `PERF_OUTBOX_RELAY_ENABLED=false` |
| 원본 로그 | `artifacts/debezium/260702_debezium_outbox_cdc_smoke_after_header_id.txt` |

## 검증 흐름

```text
outbox_events insert
  -> MySQL binlog
  -> Debezium MySQL Connector
  -> Debezium Outbox Event Router
  -> Kafka email.send
  -> backend email consumer
  -> processed_kafka_events
```

검증 조건:

- app-level Outbox Relay를 끈 상태에서 실행한다.
- Debezium connector와 task가 `RUNNING`이어야 한다.
- Kafka `email.send` topic에 smoke event가 있어야 한다.
- Kafka header `id`가 `outbox_events.id`와 일치해야 한다.
- backend consumer가 같은 event id를 `processed_kafka_events`에 저장해야 한다.
- app-level relay가 꺼져 있으므로 원본 `outbox_events` row는 `PENDING`, `published_at=NULL`이어야 한다.

## 결과

| 항목 | 결과 |
|---|---|
| backend health | `UP` |
| Debezium Connect ready | `1s` |
| Debezium connector | `RUNNING` |
| Debezium task | `RUNNING` |
| smoke event id | `162506` |
| Kafka message found | `true` |
| Kafka header event id found | `true` |
| backend consumer processed count | `1` |
| outbox status | `PENDING` |
| outbox published_at | `NULL` |
| backend relay 복구 | `JOBFLOW_OUTBOX_RELAY_ENABLED=true` |

원본 smoke summary:

```text
Debezium outbox connector is RUNNING.
debezium_smoke_event_id=162506
kafka_message_found=true
kafka_header_event_id_found=true
processed_count=1
outbox_event_status=PENDING
outbox_published_at=NULL
Debezium outbox CDC smoke completed.
```

Kafka message 확인:

```text
id:162506|8782968966|{"payload":{"to":"user@example.com","text":"Debezium outbox smoke email body","subject":"Debezium outbox smoke debezium-outbox-smoke-20260702050926","smokeRunId":"debezium-outbox-smoke-20260702050926"},"schemaVersion":1,"aggregateType":"EMAIL","aggregateId":8782968966,"eventType":"EMAIL_SEND_REQUESTED","topic":"email.send"}
```

## 해석

Debezium CDC 기반 최소 경로는 성공했다.

- `outbox_events` insert가 app-level Outbox Relay 없이 Kafka로 발행됐다.
- Debezium Outbox Event Router는 event id를 Kafka value가 아니라 header `id`에 실었다.
- backend consumer/parser가 Kafka header `id`를 idempotency event id로 해석해 `processed_kafka_events`에 처리 이력을 남겼다.
- app-level relay가 꺼져 있었기 때문에 원본 outbox row는 `PENDING`, `published_at=NULL`로 유지됐다.

따라서 이번 단계에서 확인한 것은 “Debezium connector가 RUNNING이다”가 아니라 “DB commit 이후 CDC publish와 기존 consumer idempotency 경로가 연결된다”이다.

## 한계와 후속 검증

이번 smoke는 단건 CDC path 검증이다. 이후 `260703_debezium_cdc_k6_comparison_report.md`에서 API 500VU + Outbox 10,000건 조건의 전환 전후 비교를 완료했다.

- App relay baseline: p95 `175.60ms`, p99 `341.79ms`, processed `10,000`, final lag `0`
- Debezium CDC after: p95 `128.94ms`, p99 `248.48ms`, processed `10,000`, final lag `0`
- Debezium 경로의 outbox row는 app relay처럼 `PUBLISHED`로 mark되지 않으므로, 성공 기준은 consumer processed count와 final lag다.

남은 항목은 성능 비교가 아니라 운영 장애 관측이다.

- Debezium connector stop 중 event 누적 후 restart recovery
- backend restart 중 event 유실 0건
- Grafana/Kibana error spike/recovery 캡처

기존 계획의 “consumer 코드 수정 없음”은 그대로 주장하지 않는다. 실제 Debezium Event Router 계약에 맞춰 Kafka listener/parser adapter가 header `id`를 수용하도록 변경했으며, 이메일 발송/검색 색인 side effect business logic과 idempotency 저장 모델은 유지했다.
