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

현재 기본 topic은 다음 4개다.

- `job.created`: 공고 생성/변경 이벤트 계열
- `application.events`: 지원 생성/상태 변경 이벤트 계열
- `email.send`: 이메일 발송 요청 이벤트
- `es.index`: Elasticsearch 색인 요청 이벤트

```bash
bash performance/events/ensure-kafka-topics.sh
bash performance/events/kafka-topic-smoke.sh
```

기본 기대값:

- topic: `job.created`, `application.events`, `email.send`, `es.index`
- partition: `3`
- replication factor: `1`

필요하면 환경변수로 바꿀 수 있다.

```bash
KAFKA_EXPECTED_TOPICS="job.created application.events email.send es.index" \
KAFKA_EXPECTED_PARTITIONS=3 \
KAFKA_EXPECTED_REPLICATION_FACTOR=1 \
bash performance/events/kafka-topic-smoke.sh
```

`staging-performance-up.sh`는 Kafka health check 후 이 smoke를 자동 실행한다.

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
