# 이벤트 처리 기준선 진단

이 디렉터리는 Kafka를 도입하기 전에 현재 애플리케이션 레벨 이벤트 처리 경로의 상태를 기록하기 위한 진단 도구를 둔다.

목표는 현재 API 지연 시간이 나쁘다는 것을 증명하는 것이 아니다. Round 1 k6 baseline에서는 공고 조회/검색 API가 안정적으로 동작했다. 여기서는 Kafka 전환 전에 현재 구조의 운영 상태를 남긴다.

- Outbox Relay가 애플리케이션 안에서 MySQL을 polling한다.
- 알림 배치가 애플리케이션 런타임과 DB 리소스를 함께 사용한다.
- downstream 장애나 재시도 상황에서 이벤트 backlog가 생길 수 있다.

## 실행 방법

먼저 staging performance stack을 준비한다.

```bash
REQUIRED_PORTS="" \
bash performance/deploy/staging-performance-up.sh
```

그 다음 이벤트 처리 기준선 진단을 실행한다.

```bash
bash performance/events/event-processing-baseline-check.sh
```

기본 실행 대상은 다음과 같다.

- MySQL service: `mysql`
- Database: `jobflow_perf`
- User/password: `jobflow` / `jobflow`

필요하면 환경변수로 바꿀 수 있다.

```bash
PERF_DB_NAME=jobflow_perf \
PERF_DB_USER=jobflow \
PERF_DB_PASSWORD=jobflow \
bash performance/events/event-processing-baseline-check.sh
```

## 결과 해석

`OUTBOX_RELAY_BACKLOG_SUMMARY`

- `pending_event_count`: 아직 발행되지 않은 이벤트 수
- `failed_event_count`: 재시도 후 실패 상태가 된 이벤트 수
- `retry_exhausted_pending_count`: 재시도 한도에 도달했지만 아직 pending인 이벤트 수
- `max_pending_age_seconds`: 가장 오래된 pending 이벤트의 대기 시간
- `avg_publish_latency_ms`: outbox insert 후 publish까지 걸린 평균 시간

`NOTIFICATION_RETRY_BACKLOG_SUMMARY`

- `retry_ready_count`: 지금 바로 재시도 가능한 알림 수
- `retry_scheduled_count`: 미래 시점에 재시도 예정인 알림 수
- `retry_exhausted_count`: 최대 시도 횟수에 도달한 알림 수

이 결과는 Kafka/Debezium 전환 전 상태를 기록하는 기준선으로 사용한다. 이후 Kafka 기반 처리 구조를 도입하면 API 지연 시간, 재시도 회복, backlog 처리 상태를 이 결과와 비교한다.

## Kafka topic ensure / smoke

`ensure-kafka-topics.sh`는 메시징 스택이 뜬 뒤 필수 Kafka topic을 idempotent하게 생성한다. 이미 존재하는 topic은 그대로 둔다.

`kafka-topic-smoke.sh`는 필수 Kafka topic이 생성됐는지 확인한다.

현재 기본 topic은 다음 5개다.

- `job.created`: 공고 생성/변경 이벤트 계열
- `application.events`: 지원 생성/상태 변경 이벤트 계열
- `email.send`: 이메일 발송 요청 이벤트
- `es.index`: Elasticsearch 색인 요청 이벤트
- `security.events`: Gateway 보안/접근 이벤트

```bash
bash performance/events/ensure-kafka-topics.sh
bash performance/events/kafka-topic-smoke.sh
```

기본 기대값:

- topic: `job.created`, `application.events`, `email.send`, `es.index`, `security.events`
- partition: `3`
- replication factor: `1`

필요하면 환경변수로 바꿀 수 있다.

```bash
KAFKA_EXPECTED_TOPICS="job.created application.events email.send es.index security.events" \
KAFKA_EXPECTED_PARTITIONS=3 \
KAFKA_EXPECTED_REPLICATION_FACTOR=1 \
bash performance/events/kafka-topic-smoke.sh
```

`staging-performance-up.sh`는 Kafka health check 후 이 smoke를 자동 실행한다.

## Outbox Relay Kafka publish smoke

`outbox-kafka-publish-smoke.sh`는 `outbox_events`에 테스트용 `PENDING` 이벤트를 1건 넣고, backend Outbox Relay가 해당 이벤트를 Kafka topic까지 발행하는지 확인한다.

이 smoke는 topic 존재 여부만 확인하는 `kafka-topic-smoke.sh`보다 한 단계 더 실제 애플리케이션 경로에 가깝다.

- MySQL `outbox_events` insert
- backend Outbox Relay polling
- Kafka publisher 발행
- MySQL status `PUBLISHED` 전환
- Kafka topic에서 해당 `eventId` 메시지 확인

```bash
bash performance/events/outbox-kafka-publish-smoke.sh
```

기대 결과:

```text
Outbox Kafka publish smoke completed.
```

기본 실행 대상:

- MySQL service: `mysql`
- Kafka service: `kafka`
- Database: `jobflow_perf`
- Topic: `job.created`

필요하면 환경변수로 바꿀 수 있다.

```bash
OUTBOX_SMOKE_TOPIC=job.created \
OUTBOX_SMOKE_WAIT_SECONDS=30 \
KAFKA_CONSUMER_TIMEOUT_MS=10000 \
bash performance/events/outbox-kafka-publish-smoke.sh
```

주의:

- 이 smoke는 `PERF_DB_NAME=jobflow`일 때 실행을 거부한다.
- payload에는 테스트용 `Sample Company`, `Sample backend engineer`만 사용한다.
- 실제 공고, 실제 사용자, 실제 이메일, 실제 외부 식별자는 사용하지 않는다.
- `staging-performance-up.sh`는 backend/gateway health 확인 후 이 smoke를 자동 실행한다.

## Kafka consumer smoke

`kafka-consumer-smoke.sh`는 Kafka topic에 메시지를 직접 발행한 뒤 backend consumer가 실제로 처리했는지 로그로 확인한다.

검증 경로는 두 가지다.

- `job.created` topic -> `JobSearchIndexKafkaConsumer` -> Elasticsearch 색인 요청
- `email.send` topic -> `EmailSendKafkaConsumer` -> mock email sender 호출

```bash
bash performance/events/kafka-consumer-smoke.sh
```

기대 결과:

```text
Kafka consumer smoke completed.
```

기본 실행 대상:

- Kafka service: `kafka`
- Backend service: `backend`
- MySQL service: `mysql`
- Database: `jobflow_perf`
- Topic: `job.created`, `email.send`

주의:

- 이 smoke는 `PERF_DB_NAME=jobflow`일 때 실행을 거부한다.
- `email.send` 메시지는 `user@example.com`과 smoke run id가 포함된 테스트 제목만 사용한다.
- 실제 사용자 이메일, 실제 공고, 실제 외부 식별자는 사용하지 않는다.
- `staging-performance-up.sh`는 Outbox Kafka publish smoke 이후 이 smoke를 자동 실행한다.

## Kafka consumer latency / lag scenario

`run-kafka-consumer-latency-lag-scenario.sh`는 API 부하가 걸린 동안 Kafka consumer 경로가 이벤트를 따라가는지 확인한다.

두 대의 EC2를 분리해서 사용할 때는 staging-performance 서버에서 `prepare`/`finish` phase를 실행하고, k6-runner 서버에서는 `k6-only` phase만 실행한다. 이렇게 해야 부하를 받는 서버와 부하를 만드는 서버가 분리되어 API latency가 k6 프로세스와 같은 CPU를 공유해 왜곡되는 문제를 줄일 수 있다.

검증 포인트는 세 가지다.

## Debezium Outbox CDC smoke

`performance/debezium/register-outbox-connector.sh`는 Debezium MySQL Connector를 등록한다. Connector는 `outbox_events` table의 insert를 MySQL binlog에서 읽고, Debezium Outbox Event Router SMT로 `topic` column 값에 해당하는 Kafka topic에 메시지를 발행한다.

`performance/debezium/debezium-outbox-cdc-smoke.sh`는 app-level Outbox Relay를 끈 상태에서 다음 경로를 확인한다.

- MySQL `outbox_events` insert
- Debezium MySQL Connector CDC capture
- Kafka `email.send` topic publish
- 기존 backend Kafka consumer 처리
- `processed_kafka_events` idempotency record 생성
- app-level relay가 꺼져 있으므로 outbox row `status=PENDING`, `published_at=NULL` 유지

실행 전 backend는 반드시 `PERF_OUTBOX_RELAY_ENABLED=false`로 재기동해야 한다. 이 조건이 아니면 기존 app-level relay가 같은 outbox row를 publish할 수 있으므로 smoke가 실패한다.

```bash
PERF_OUTBOX_RELAY_ENABLED=false \
docker compose -f docker-compose.yml -f docker-compose.performance.yml up -d --force-recreate backend debezium-connect

bash performance/debezium/debezium-outbox-cdc-smoke.sh
```

기대 결과:

```text
Debezium outbox CDC smoke completed.
```

주의:

- 이 smoke는 `PERF_DB_NAME=jobflow`일 때 실행 불가능하다.
- 기존 performance DB에 `schema_version` column이 없으면 smoke가 performance DB에 한해 column을 추가한다.
- Debezium은 outbox row를 Kafka로 발행하지만 app DB row의 publish status를 직접 바꾸지 않는다. Debezium 전환 이후 `PENDING`은 app relay의 publish 대기 상태가 아니라 cleanup/retention 정책의 대상이 된다.

## Debezium k6 comparison / recovery

`performance/debezium/run-debezium-k6-comparison.sh`는 app-level Outbox Relay와 Debezium CDC를 같은 k6 조건에서 비교하기 위한 runner다.

비교군은 두 가지다.

- `MODE=app-relay-baseline`: 기존 app-level Outbox Relay가 `outbox_events`를 polling해 Kafka로 publish한다.
- `MODE=debezium-cdc-after`: app-level Outbox Relay를 끄고 Debezium MySQL Connector가 binlog 기반으로 Kafka에 publish한다.

검증 포인트:

- API 500VU / 5분 기준 p95, p99, error rate
- Outbox 대량 이벤트 처리 수
- `processed_kafka_events` 처리 수
- Kafka consumer lag: `kafka-consumer-groups --describe` snapshot과 Prometheus `kafka_consumergroup_lag`
- Kafka consumer final lag 0
- duplicate replay idempotency: 같은 `eventId`의 `email.send` 메시지를 2번 발행했을 때 `processed_kafka_events`가 1건만 남는지
- Debezium 전환 후에도 기존 side effect business logic과 idempotency 모델 유지

단일 서버에서 빠르게 실행할 때:

```bash
MODE=app-relay-baseline \
PHASE=full \
KAFKA_EVENT_LOAD_COUNT=10000 \
bash performance/debezium/run-debezium-k6-comparison.sh

MODE=debezium-cdc-after \
PHASE=full \
KAFKA_EVENT_LOAD_COUNT=10000 \
bash performance/debezium/run-debezium-k6-comparison.sh
```

두 대의 EC2를 분리해서 측정할 때는 staging-performance 서버에서 `prepare`와 `seed`/`finish`를 실행하고, k6-runner 서버에서는 같은 branch/script 기준으로 `k6-only`를 실행한다. 이 방식이 포트폴리오 대표 수치에는 더 적합하다.

staging-performance 서버:

```bash
MODE=debezium-cdc-after \
PHASE=prepare \
DEBEZIUM_K6_RUN_ID=debezium-k6-after-$(date +%Y%m%d%H%M%S) \
bash performance/debezium/run-debezium-k6-comparison.sh
```

k6-runner 서버:

```bash
MODE=debezium-cdc-after \
PHASE=k6-only \
DEBEZIUM_K6_RUN_ID=<prepare에서 사용한 run id> \
BASE_URL=http://172.31.63.104:8080/api \
bash performance/debezium/run-debezium-k6-comparison.sh
```

k6 실행 중 staging-performance 서버:

```bash
MODE=debezium-cdc-after \
PHASE=seed \
DEBEZIUM_K6_RUN_ID=<prepare에서 사용한 run id> \
KAFKA_EVENT_LOAD_COUNT=10000 \
bash performance/debezium/run-debezium-k6-comparison.sh
```

k6 종료 후 staging-performance 서버:

```bash
MODE=debezium-cdc-after \
PHASE=finish \
DEBEZIUM_K6_RUN_ID=<prepare에서 사용한 run id> \
KAFKA_EVENT_LOAD_COUNT=10000 \
bash performance/debezium/run-debezium-k6-comparison.sh
```

기대 결과:

```text
Debezium k6 comparison scenario completed.
processed_count=10000
total_lag=0
```

`performance/debezium/run-debezium-recovery-scenario.sh`는 Debezium 전환 후 장애 복구를 확인한다.

- `DEBEZIUM_RECOVERY_MODE=connector-paused`: connector pause 중 outbox insert 후 resume drain 확인
- `DEBEZIUM_RECOVERY_MODE=backend-restart`: backend consumer disabled 중 Kafka lag 누적 후 consumer enabled drain 확인
- `DEBEZIUM_RECOVERY_MODE=all`: 두 시나리오를 연속 실행

```bash
DEBEZIUM_RECOVERY_MODE=all \
DEBEZIUM_RECOVERY_EVENT_COUNT=10000 \
bash performance/debezium/run-debezium-recovery-scenario.sh
```

기대 결과:

```text
Debezium recovery scenario completed.
processed_count=10000
final lag 0
```

### 1. API-only baseline

이 실행은 Kafka consumer는 켜두되 추가 이벤트 부하는 넣지 않는다. 같은 서버/DB/검색 인덱스에서 API 자체 기준선을 잡기 위한 비교군이다.

```bash
PHASE=full \
SCENARIO=api-only-baseline \
BASE_URL=http://localhost:8081/api \
LOGIN_EMAIL='frontend-demo@example.com' \
LOGIN_PASSWORD='password123' \
VUS=500 \
DURATION=5m \
bash performance/events/run-kafka-consumer-latency-lag-scenario.sh
```

### 2. Kafka consumer active under event load

이 실행은 `outbox_events`에 `email.send` 이벤트를 대량으로 넣고, Outbox Relay가 Kafka로 발행하며, backend consumer가 처리하는 동안 같은 k6 부하를 건다.

```bash
PHASE=full \
SCENARIO=kafka-consumer-after \
BASE_URL=http://localhost:8081/api \
LOGIN_EMAIL='frontend-demo@example.com' \
LOGIN_PASSWORD='password123' \
VUS=500 \
DURATION=5m \
KAFKA_EVENT_LOAD_COUNT=120000 \
bash performance/events/run-kafka-consumer-latency-lag-scenario.sh
```

### 3. Kafka direct topic burst for lag spike/drain

이 실행은 Outbox Relay를 거치지 않고 `email.send` topic에 테스트 메시지를 직접 발행한다. 목적은 API 성능 비교가 아니라 Kafka consumer lag 패널이 실제 backlog를 관측하고, consumer가 backlog를 다시 0까지 drain하는지 확인하는 것이다.

공식 결과를 해석할 때는 다음처럼 구분한다.

- Outbox 경유 시나리오: DB outbox insert -> Outbox Relay -> Kafka publish -> consumer 처리까지 포함한 end-to-end 경로
- Direct topic burst: Kafka topic -> consumer 처리 구간만 강제로 압축해 lag spike/drain을 관측하는 운영성 검증

```bash
KAFKA_DIRECT_BURST_COUNT=10000 \
KAFKA_DIRECT_BURST_RUN_ID=kafka-direct-burst-$(date +%Y%m%d%H%M%S) \
bash performance/events/seed-kafka-email-topic-burst.sh
```

실행 직후 Grafana `Kafka Consumer Lag` 패널과 다음 snapshot을 함께 남긴다.

```bash
SNAPSHOT_LABEL=$(date +%Y%m%d%H%M%S)_direct_burst_after_publish \
bash performance/events/kafka-consumer-lag-snapshot.sh
```

### 4. Two-server execution

staging-performance 서버에서 prepare:

```bash
PHASE=prepare \
SCENARIO=kafka-consumer-after \
KAFKA_EVENT_LOAD_COUNT=120000 \
bash performance/events/run-kafka-consumer-latency-lag-scenario.sh
```

k6-runner 서버에서 k6-only:

```bash
PHASE=k6-only \
SCENARIO=kafka-consumer-after \
BASE_URL=http://3.39.242.44:8081/api \
LOGIN_EMAIL='frontend-demo@example.com' \
LOGIN_PASSWORD='password123' \
VUS=500 \
DURATION=5m \
bash performance/events/run-kafka-consumer-latency-lag-scenario.sh
```

k6 종료 직후 staging-performance 서버에서 finish:

```bash
PHASE=finish \
SCENARIO=kafka-consumer-after \
bash performance/events/run-kafka-consumer-latency-lag-scenario.sh
```

기본 산출물 위치:

- k6 JSON: `artifacts/kafka/날짜_kafka_consumer_latency_lag/*_k6.json`
- consumer group lag snapshot: `artifacts/kafka/날짜_kafka_consumer_latency_lag/*_consumer_group_lag.txt`
- Prometheus lag query JSON: `artifacts/kafka/날짜_kafka_consumer_latency_lag/*_prometheus_kafka_lag.json`
- Prometheus current offset query JSON: `artifacts/kafka/날짜_kafka_consumer_latency_lag/*_prometheus_kafka_current_offset.json`
- event baseline: `artifacts/kafka/날짜_kafka_consumer_latency_lag/*_event_baseline_{before,after}.txt`
- duplicate replay smoke: `artifacts/kafka/날짜_kafka_consumer_latency_lag/*_idempotency_smoke.txt`

주의:

- 이 시나리오는 `PERF_DB_NAME=jobflow`일 때 실행을 거부한다.
- 이벤트 부하는 `user@example.com`, `Sample`, smoke run id만 사용한다.
- 실제 이메일, 실제 사용자, 실제 외부 식별자는 사용하지 않는다.
- `api-only-baseline`과 `kafka-consumer-after`를 같은 서버/같은 fixture 상태에서 연달아 실행해야 API p95 변화와 lag 회복 여부를 비교할 수 있다.
- Grafana 캡처는 k6 steady-state 중간 1회, 종료 직후 1회 수집한다. 특히 `Kafka Consumer Lag`, `HikariCP Connections`, `P95 / P99 Latency`, `HTTP Request Rate` 패널이 보이게 캡처한다.

## Kafka failure / recovery scenarios

| 질문 | 검증 스크립트 | 확인하는 것 |
|---|---|---|
| consumer가 멈추면 메시지는 유실되나? | `run-kafka-consumer-recovery-scenario.sh` | consumer disabled 상태에서 lag 누적, consumer 재기동 후 lag 0 drain |
| consumer 처리 실패는 어디에 남나? | `kafka-dlq-poison-retry-smoke.sh` | poison message DLQ topic 발행, `dlq_messages` 저장, admin retry-by-id |
| 같은 메시지가 두 번 오면 side effect가 중복되나? | `kafka-duplicate-replay-idempotency-smoke.sh` | 같은 `eventId` 2회 발행 후 `processed_kafka_events` 1건 유지 |
| 같은 aggregate의 순서는 어떻게 지키나? | `kafka-partition-key-order-smoke.sh` | 같은 key가 같은 partition에 기록되고 publish 순서가 유지됨 |

### 1. Consumer down -> lag accumulation -> recovery

이 시나리오는 backend를 `JOBFLOW_KAFKA_CONSUMER_ENABLED=false`로 재기동해 consumer만 끈다. Outbox Relay는 계속 Kafka로 발행하므로 topic lag가 쌓여야 한다. 이후 consumer를 다시 켜서 lag가 0까지 drain 되는지 확인한다.

```bash
KAFKA_RECOVERY_EVENT_COUNT=10000 \
KAFKA_RECOVERY_RUN_ID=kafka-consumer-recovery-$(date +%Y%m%d%H%M%S) \
bash performance/events/run-kafka-consumer-recovery-scenario.sh
```

기대 결과:

- consumer disabled 구간에서 `lag_accumulated > 0`
- consumer enabled 재기동 후 `final_lag=0`
- `processed_count`가 주입한 outbox event 수와 일치
- Grafana `Kafka Consumer Lag` 패널에서 lag 누적과 drain이 보임

캡처 타이밍:

- consumer disabled 후 lag가 쌓인 시점
- consumer enabled 재기동 직후 drain 중인 시점
- lag가 0으로 복구된 시점

### 2. Poison message -> DLQ persistence -> retry-by-id

이 시나리오는 `email.send` topic에 필수 필드가 빠진 poison message를 발행한다. consumer retry가 끝나면 recoverer가 원본 메시지를 DLQ envelope로 감싸고, DLQ topic 발행과 `dlq_messages` 저장을 수행한다. 이후 admin retry-by-id API가 저장된 envelope를 원본 topic으로 재발행하고 기존 DLQ row를 `RETRIED`로 바꾸는지 검증한다.

```bash
ADMIN_ACCESS_TOKEN='...' \
KAFKA_DLQ_SMOKE_RUN_ID=kafka-dlq-poison-$(date +%Y%m%d%H%M%S) \
bash performance/events/kafka-dlq-poison-retry-smoke.sh
```

기대 결과:

- 최초 poison message가 `dlq_messages.status=PENDING`으로 저장됨
- `GET /admin/dlq/messages/{id}`가 저장된 envelope를 반환함
- `POST /admin/dlq/messages/{id}/retry`가 `202 Accepted`로 응답함
- 원본 DLQ row가 `RETRIED`, `retry_count=1`로 바뀜
- poison payload를 그대로 재발행하므로 재시도된 메시지는 다시 실패할 수 있다. 이 smoke의 목적은 retry API가 원본 topic으로 재발행하고 운영 상태를 남기는지 확인하는 것이다. payload 수정/보정 후 재처리는 별도 운영 정책 범위다.

주의:

- `ADMIN_ACCESS_TOKEN`은 admin 권한 JWT다. 스크립트 출력에 token 값을 남기지 않는다.
- 이 스크립트는 `PERF_DB_NAME=jobflow`이면 실행을 거부한다.

### 3. Duplicate replay idempotency

같은 `eventId`의 `email.send` 메시지를 2번 발행해, consumer side effect가 한 번만 실행되는지 확인한다. Kafka exactly-once transaction을 쓴 것이 아니라 at-least-once delivery 위에서 `processed_kafka_events` unique key로 effectively-once 처리를 만든다는 근거다.

```bash
KAFKA_IDEMPOTENCY_SMOKE_RUN_ID=kafka-idempotency-$(date +%Y%m%d%H%M%S) \
bash performance/events/kafka-duplicate-replay-idempotency-smoke.sh
```

기대 결과:

- `processed_count=1`
- backend log에 duplicate skip이 남음

### 4. Partition key / order smoke

Kafka는 partition 내부 순서만 보장한다. 따라서 같은 aggregate에 대한 이벤트 순서가 필요하면 같은 partition으로 가도록 key를 고정해야 한다. 이 smoke는 동일 key로 여러 메시지를 발행하고, consumer formatter의 partition 출력과 sequence 값을 확인한다.

```bash
KAFKA_PARTITION_SMOKE_COUNT=5 \
KAFKA_PARTITION_SMOKE_RUN_ID=kafka-partition-key-$(date +%Y%m%d%H%M%S) \
bash performance/events/kafka-partition-key-order-smoke.sh
```

기대 결과:

- `matched_count=5`
- `partition_count=1`
- `sequence_list=1,2,3,4,5`

이 결과는 “같은 key는 같은 partition으로 간다”는 smoke다. 전체 Kafka topic 순서를 보장한다는 뜻은 아니다.

## Security event pipeline smoke

`security-event-pipeline-smoke.sh`는 Gateway가 `security.events` topic으로 발행한 보안 이벤트를 Logstash가 Elasticsearch에 적재하는지 확인한다.

검증 경로는 다음과 같다.

- Gateway에 `X-Request-Id`가 포함된 비정상 요청 전송
- Gateway security event filter가 `ABNORMAL_REQUEST` 이벤트 발행
- Kafka `security.events` topic 수신
- Logstash Kafka input이 이벤트 소비
- Elasticsearch `jobflow-security-events` 인덱스 적재 확인

```bash
bash performance/security/security-event-pipeline-smoke.sh
```

기대 결과:

```text
Security event pipeline smoke completed.
```

기본 요청은 `/api/.env`이며, 이 요청은 민감 경로 probe로 분류되어 `ABNORMAL_REQUEST` 이벤트가 되어야 한다.

필요하면 환경변수로 바꿀 수 있다.

```bash
SMOKE_REQUEST_PATH=/api/.git/config \
SMOKE_WAIT_SECONDS=60 \
bash performance/security/security-event-pipeline-smoke.sh
```

`staging-performance-up.sh`는 backend/gateway health 확인 후 이 smoke를 자동 실행한다.

## 시나리오 A: 알림 배치 on/off 비교

`run-deadline-reminder-contention-scenario.sh`는 마감 알림 배치가 꺼진 상태와 켜진 상태를 같은 k6 조건으로 비교하기 위한 실행 스크립트다.

이 시나리오의 목적은 “API가 반드시 느려진다”를 단정하는 것이 아니다. 같은 서버, 같은 DB, 같은 k6 조건에서 알림 배치가 애플리케이션 런타임과 MySQL을 함께 사용할 때 지연 시간, 실패율, 알림 처리 backlog가 어떻게 달라지는지 기록하는 것이다.

### 1. 배치 off 기준선

```bash
SCENARIO=scheduler-off \
BASE_URL=http://localhost:8081/api \
LOGIN_EMAIL='frontend-demo@example.com' \
LOGIN_PASSWORD='password123' \
VUS=20 \
DURATION=5m \
K6_SUMMARY_EXPORT=/tmp/jobflow-k6-deadline-reminder-scheduler-off.json \
bash performance/events/run-deadline-reminder-contention-scenario.sh
```

기대 결과:

- `checks` threshold 통과
- `http_req_failed` 0%에 가깝게 유지
- `OUTBOX_RELAY_BACKLOG_SUMMARY`, `NOTIFICATION_RETRY_BACKLOG_SUMMARY` 출력

### 2. 배치 on 비교군

```bash
SCENARIO=scheduler-on \
BASE_URL=http://localhost:8081/api \
LOGIN_EMAIL='frontend-demo@example.com' \
LOGIN_PASSWORD='password123' \
VUS=20 \
DURATION=5m \
K6_SUMMARY_EXPORT=/tmp/jobflow-k6-deadline-reminder-scheduler-on.json \
bash performance/events/run-deadline-reminder-contention-scenario.sh
```

기대 결과:

- 마감 알림 fixture가 준비된다.
- backend/gateway가 `DEADLINE_REMINDER_SCHEDULER_ENABLED=true` 상태로 재기동된다.
- k6 실행 전후로 알림 처리 상태가 출력된다.
- 배치 off 결과와 `p95`, `p99`, `http_req_failed`, 알림 처리량을 비교할 수 있다.

### 실행 시 주의

- 이 시나리오는 `jobflow_perf` 같은 성능 전용 DB에서만 실행해야 한다.
- 알림 fixture는 `NOTIFICATION_MOCK_LOAD` source와 `example.com` 계정만 사용한다.
- 실제 Mailgun 발송을 하지 않도록 performance compose에서 mock email sender를 사용한다.
- 알림 fixture가 `/jobs` 결과 순서에 섞일 수 있으므로 이 runner는 기본적으로 `EXPECT_PERF_FIXTURE=false`로 k6를 실행한다.

## 시나리오 B: 알림 provider 실패와 retry backlog

`run-deadline-reminder-provider-failure-scenario.sh`는 mock email provider를 실패 모드로 켠 뒤 마감 알림 배치를 실행한다.

이 시나리오의 목적은 downstream 장애 상황에서 알림 이벤트가 어떻게 남는지 확인하는 것이다. Kafka topic 발행 자체는 topic smoke와 relay publish 검증에서 확인하고, 이 시나리오는 실제 실패/재시도 상태가 존재하는 알림 발송 경로를 사용해 retry backlog를 기록한다.

```bash
BASE_URL=http://localhost:8081/api \
LOGIN_EMAIL='frontend-demo@example.com' \
LOGIN_PASSWORD='password123' \
VUS=20 \
DURATION=5m \
K6_SUMMARY_EXPORT=/tmp/jobflow-k6-deadline-reminder-provider-failure.json \
bash performance/events/run-deadline-reminder-provider-failure-scenario.sh
```

기대 결과:

- backend/gateway가 mock email 실패 모드로 재기동된다.
- `notification_attempts`에는 `FAILED` attempt가 쌓인다.
- `notification_logs`에는 `PENDING` 및 미래 `next_retry_at`이 남는다.
- k6는 API 지연 시간과 실패율을 기록하고, 이벤트 진단 SQL은 retry backlog 상태를 출력한다.

## 시나리오 C: 재시작 후 retry recovery

`run-deadline-reminder-retry-recovery-scenario.sh`는 실패한 알림 backlog를 즉시 재시도 가능 상태로 바꾼 뒤 backend/gateway를 정상 mock email provider로 재기동한다.

현재 outbox에는 `PROCESSING` 상태가 없다. 상태는 `PENDING`, `PUBLISHED`, `FAILED`만 존재한다. 그래서 이 시나리오는 “PROCESSING stuck”이 아니라, 서버 재시작 후 pending retry 이벤트가 정상적으로 다시 처리되는지를 검증한다.

```bash
OBSERVE_SECONDS=30 \
bash performance/events/run-deadline-reminder-retry-recovery-scenario.sh
```

기대 결과:

- 실패 backlog가 없으면 먼저 provider failure 시나리오를 짧게 실행해 실패 데이터를 만든다.
- `deadline-reminder-retry-ready.sql`이 pending 알림의 `next_retry_at`을 현재 시각으로 당긴다.
- backend/gateway가 정상 mock email provider로 재기동된다.
- 재기동 후 `retry_ready_count`가 줄고 `sent_count`가 늘어나는지 확인한다.

복구 결과를 별도로 보고 싶으면 실패 backlog를 먼저 만든 뒤 아래처럼 recovery만 실행할 수 있다.

```bash
PREPARE_FAILURE_BACKLOG=false \
OBSERVE_SECONDS=30 \
bash performance/events/run-deadline-reminder-retry-recovery-scenario.sh
```
