# Event Processing Reliability Baseline Report

## 목적

이번 측정은 Kafka, Debezium 도입 전 현재 이벤트 처리 구조의 신뢰성 기준선을 남기기 위한 작업이다.

공고 조회 API의 단순 latency만으로는 이벤트 처리 병목이나 provider 실패 상황을 설명하기 어렵다. 따라서 deadline reminder 배치를 기준으로 다음 세 가지 시나리오를 재현했다.

- 배치 off / on 상태에서 API p95, p99 latency 변화 확인
- notification provider 실패 시 API는 정상이어도 내부 backlog가 쌓이는지 확인
- retry 가능 상태로 전환한 뒤 pending backlog가 정상적으로 회복되는지 확인

이 문서는 Kafka / Debezium 도입 전의 before 기준선이다.

## 측정 환경

| 항목 | 값 |
| --- | --- |
| 배포 환경 | AWS EC2 단일 인스턴스 |
| 배포 방식 | Docker Compose performance stack |
| 주요 서비스 | gateway, backend, MySQL, Redis, Elasticsearch, Prometheus, Grafana |
| 성능 DB | `jobflow_perf` |
| 성능 fixture | 1,000 jobs |
| k6 실행 위치 | EC2 내부 |
| k6 호출 대상 | `http://localhost:8081/api` |
| k6 조건 | 20 VU, 5분 |
| 대상 API | `/jobs`, `/jobs/search`, `/recommendations/jobs`, `/gap-analysis/projects/{userProjectId}` |
| 관측 도구 | k6 summary, MySQL baseline query, Grafana backend observability dashboard |

이번 시나리오는 외부 사용자 latency 측정보다 서버 내부 경합과 event backlog 재현이 목적이다. 따라서 k6는 EC2 내부에서 실행했다. 외부 클라이언트 기준 latency는 별도 Round 1 baseline report의 external baseline을 기준으로 사용한다.

## 시나리오 A: Deadline Reminder 배치 경합

### 실행 조건

동일한 20 VU / 5분 조건에서 deadline reminder scheduler를 끈 상태와 켠 상태를 비교했다.

| 조건 | 설명 |
| --- | --- |
| scheduler-off | deadline reminder scheduler 비활성화 |
| scheduler-on | deadline reminder scheduler 활성화, 10,000건 reminder 발송 |

### API 성능 비교

| 지표 | Scheduler off | Scheduler on | 변화 |
| --- | ---: | ---: | ---: |
| check success rate | 100.00% | 100.00% | 0.00%p |
| HTTP failure rate | 0.00% | 0.00% | 0.00%p |
| HTTP request rate | 76.12 req/s | 75.43 req/s | -0.91% |
| 전체 p95 latency | 26.67ms | 36.13ms | +35.46% |
| 전체 p99 latency | 94.45ms | 136.32ms | +44.33% |
| `/jobs` p95 | 33.48ms | 49.23ms | +47.01% |
| `/jobs/search` p95 | 29.61ms | 41.72ms | +40.86% |
| `/recommendations/jobs` p95 | 22.78ms | 28.88ms | +26.73% |
| `/gap-analysis/projects/{userProjectId}` p95 | 12.98ms | 16.75ms | +29.06% |

### 이벤트 처리 결과

| 지표 | Scheduler off | Scheduler on |
| --- | ---: | ---: |
| notification total | 0 | 10,000 |
| notification pending | 0 | 0 |
| notification sent | 0 | 10,000 |
| notification failed | 0 | 0 |
| max attempt count | 0 | 1 |

### 해석

배치를 켠 상태에서도 API error rate는 0%로 유지됐다. 다만 전체 p95 latency는 26.67ms에서 36.13ms로 증가했고, `/jobs` p95는 33.48ms에서 49.23ms로 증가했다.

현재 부하 조건에서는 threshold를 넘는 장애는 발생하지 않았다. 그러나 백그라운드 notification batch가 API tail latency에 영향을 줄 수 있다는 기준선은 확인했다.

## 시나리오 B: Provider 실패와 Retry Backlog

### 실행 조건

Mock email provider를 실패 모드로 전환한 뒤 deadline reminder scheduler를 실행했다. 동시에 k6로 API 부하를 유지해 provider 실패가 사용자 API에 직접 오류로 전파되는지 확인했다.

### API 성능

| 지표 | 결과 |
| --- | ---: |
| check success rate | 100.00% |
| HTTP failure rate | 0.00% |
| HTTP request rate | 75.44 req/s |
| 전체 p95 latency | 37.93ms |
| 전체 p99 latency | 118.31ms |
| `/jobs` p95 | 52.43ms |
| `/jobs/search` p95 | 51.25ms |
| `/recommendations/jobs` p95 | 33.17ms |
| `/gap-analysis/projects/{userProjectId}` p95 | 19.10ms |

### 이벤트 처리 결과

| 지표 | 결과 |
| --- | ---: |
| notification total | 10,000 |
| notification pending | 10,000 |
| notification sent | 0 |
| notification failed | 0 |
| retry scheduled | 10,000 |
| retry exhausted | 0 |
| failed attempt count | 10,000 |

### 해석

Provider 실패 상태에서도 API failure rate는 0%였다. 사용자 API는 정상 응답을 유지했지만, 내부적으로는 10,000건의 notification이 pending 상태로 남고 retry schedule에 들어갔다.

즉 API 지표만 보면 정상처럼 보이지만, 이벤트 처리 관점에서는 backlog가 발생했다. 이후 Kafka / Debezium 도입 또는 consumer 분리 효과를 설명하려면 HTTP latency뿐 아니라 event backlog, retry age, consumer lag 같은 business metric이 필요하다.

## 시나리오 C: Retry Recovery

### 실행 조건

시나리오 B에서 생성한 10,000건 pending notification을 retry 가능한 상태로 전환한 뒤, provider를 정상 모드로 되돌리고 recovery를 관측했다.

### Recovery 전후 비교

| 지표 | Recovery 전 | Recovery 후 |
| --- | ---: | ---: |
| notification total | 10,000 | 10,000 |
| pending | 10,000 | 0 |
| sent | 0 | 10,000 |
| failed | 0 | 0 |
| retry ready | 10,000 | 0 |
| retry scheduled | 0 | 0 |
| retry exhausted | 0 | 0 |
| total attempt count | 10,000 | 20,000 |
| max attempt count | 1 | 2 |
| failed attempt count | 10,000 | 10,000 |
| sent attempt count | 0 | 10,000 |

### 해석

Retry recovery 후 pending 10,000건은 모두 SENT 상태로 전환됐다. 실패 attempt 10,000건은 이력으로 남고, 두 번째 attempt에서 10,000건이 성공했다.

현재 retry 구조는 provider가 회복되면 backlog를 처리할 수 있다. 다만 backlog 발생 자체는 API error rate로 드러나지 않으므로, 이후 구조 개선에서는 event 처리 지연과 retry backlog를 별도 지표로 관측해야 한다.

## Grafana 관측

Grafana `JobFlow Backend Observability` dashboard에서 다음 항목을 함께 확인했다.

- HTTP request rate
- P95 / P99 latency
- Error rate
- Cache hit rate
- JVM heap / non-heap memory

모든 시나리오에서 error rate는 0%로 유지됐다. Cache hit rate는 0%로 표시되었으며, 이번 작업은 cache 최적화가 아니라 event 처리 reliability baseline이므로 별도 개선 대상으로 분리한다.

## 산출물

Raw JSON, log, Grafana screenshot은 Git에 커밋하지 않고 로컬 artifact로 보관한다.

보관 artifact:

- `jobflow-k6-deadline-reminder-scheduler-off.json`
- `jobflow-k6-deadline-reminder-scheduler-on.json`
- `jobflow-k6-deadline-reminder-provider-failure.json`
- `jobflow-k6-deadline-reminder-provider-failure-for-recovery.json`
- `jobflow-deadline-reminder-scheduler-off.log`
- `jobflow-deadline-reminder-scheduler-on.log`
- `jobflow-deadline-reminder-provider-failure.log`
- `jobflow-deadline-reminder-retry-recovery.log`
- `grafana_a_scheduler_off_20vu_5m.png`
- `grafana_a_scheduler_on_20vu_5m.png`
- `grafana_b_provider_failure_20vu_5m.png`
- `grafana_c_retry_recovery.png`

## 결론

이번 baseline에서 확인한 사실은 세 가지다.

1. Deadline reminder 배치는 error를 만들지는 않았지만 API p95 / p99 latency를 증가시켰다.
2. Provider 실패 시 API는 정상 응답을 유지했지만 내부 notification backlog 10,000건이 발생했다.
3. Provider 회복 후 retry recovery를 통해 pending 10,000건이 모두 SENT로 전환됐다.

따라서 이후 Kafka / Debezium 전환 작업에서는 단순 HTTP latency뿐 아니라 event backlog, retry age, consumer lag, event throughput을 함께 비교해야 한다.

## 다음 단계

- Kafka 도입 전후로 event 처리 경로와 backlog 해소 시간을 비교한다.
- Debezium CDC 전환 시 app-level relay 책임이 얼마나 줄어드는지 정리한다.
- stress test에서 HikariCP pool wait이 관측되면 별도 DB connection tuning 작업으로 분리한다.
- provider retry backlog의 age 계산은 future retry schedule에서 음수로 보일 수 있으므로, 후속 metric에서는 `retry_ready_count`, `retry_scheduled_count`, `oldest_notification_created_at`을 함께 본다.
