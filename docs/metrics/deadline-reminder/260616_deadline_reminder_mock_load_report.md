# Deadline Reminder Mock Load Report

## 목적

마감 알림 이메일 배치가 대량 후보를 처리할 때 중복 발송 없이 안정적으로 동작하는지 확인한다.

실제 Mailgun 대량 발송은 비용, sandbox 제한, 스팸/계정 평판 리스크가 있으므로 사용하지 않는다. 이번 검증은 mock email provider를 사용해 batch 처리량, retry 흐름, DB audit log, idempotency 결과를 확인한다.

검증 범위:

- 대량 `SAVED` + due-soon 공고 후보 생성
- mock email provider 기반 due-soon batch 성공 처리
- mock email provider 실패 모드 기반 실패 attempt 생성
- retry window 조정 후 retry mode 성공 처리
- `notification_logs` 중복 생성 여부 확인
- `notification_attempts` attempt number 중복 여부 확인
- 실패 후 재시도 성공 흐름 확인

## 측정 기준

| 항목 | 값 |
| --- | --- |
| 측정일 | `2026-06-16` |
| DB | local MySQL `jobflow` |
| 실행 방식 | `ApplicationRunner` non-web bootRun |
| Provider | Mock email provider |
| Runner mode | `due-soon`, `retry` |
| Fixture SQL | `performance/sql/deadline-reminder-mock-load-fixture.sql` |
| Success check SQL | `performance/sql/deadline-reminder-mock-load-check.sql` |
| Retry 준비 SQL | `performance/sql/deadline-reminder-mock-load-ready-retry.sql` |
| Retry check SQL | `performance/sql/deadline-reminder-mock-load-retry-check.sql` |
| Smoke script | `performance/notification/deadline-reminder-mock-load-smoke.sh` |

민감정보는 기록하지 않는다.

- 실제 Mailgun API key를 사용하지 않는다.
- 실제 수신자 이메일을 사용하지 않는다.
- 실제 provider domain이나 provider message id 원문을 기록하지 않는다.

## Fixture

| Metric | 값 |
| --- | ---: |
| configured user count | 20 |
| configured jobs per user | 25 |
| inserted user count | 20 |
| inserted job count | 500 |
| inserted saved user job count | 10,000 |
| app minutes until deadline | 360 |

해석:

- 20명의 mock user를 만들었다.
- 500개의 mock due-soon job을 만들었다.
- 모든 mock user가 모든 mock job을 저장한 상태로 10,000개의 batch 후보를 만들었다.
- deadline은 app clock 기준 6시간 뒤라 24시간 마감 알림 window 안에 있다.

## 성공 경로

실행:

```bash
bash performance/notification/deadline-reminder-mock-load-smoke.sh
```

결과:

| Metric | 값 |
| --- | ---: |
| targetCount | 10,000 |
| sentCount | 10,000 |
| failedCount | 0 |
| skippedCount | 0 |

DB 검증:

| Metric | 값 |
| --- | ---: |
| mock saved user job count | 10,000 |
| notification log count | 10,000 |
| notification attempt count | 10,000 |
| sent log count | 10,000 |
| failed attempt count | 0 |
| remaining due-soon candidate count | 0 |
| duplicate notification log | 0 |
| duplicate attempt number | 0 |

해석:

- due-soon batch가 10,000개 후보를 모두 처리했다.
- 각 후보마다 `notification_logs`가 1개씩 생성됐다.
- 각 후보마다 `notification_attempts`가 1개씩 생성됐다.
- 모든 log가 `SENT` 상태가 됐다.
- 처리 후 남은 due-soon 후보가 없다.
- `notification_logs` unique key와 `notification_attempts` unique key 기준 중복이 없다.

## 실패 경로

실행:

```bash
MOCK_EMAIL_FAIL=true \
MOCK_EMAIL_FAILURE_REASON='mock provider failure' \
bash performance/notification/deadline-reminder-mock-load-smoke.sh
```

결과:

| Metric | 값 |
| --- | ---: |
| targetCount | 10,000 |
| sentCount | 0 |
| failedCount | 10,000 |
| skippedCount | 0 |

해석:

- mock provider 실패 모드에서 10,000개 후보가 모두 실패 attempt로 기록됐다.
- 실패한 log는 retry 대상이 될 수 있도록 `PENDING` 상태와 `next_retry_at`을 가진다.
- 실패 시 Redis idempotency key는 release되어 이후 retry가 가능하다.

## Retry 준비

실행:

```sql
-- performance/sql/deadline-reminder-mock-load-ready-retry.sql
```

결과:

| Metric | 값 |
| --- | ---: |
| retry ready notification count | 10,000 |

해석:

- 실패한 `PENDING` log 10,000건을 app clock 기준 retry 가능 window로 이동했다.

## Retry 성공 경로

실행:

```bash
MODE=retry \
bash performance/notification/deadline-reminder-mock-load-smoke.sh
```

결과:

| Metric | 값 |
| --- | ---: |
| targetCount | 10,000 |
| sentCount | 10,000 |
| failedCount | 0 |
| skippedCount | 0 |

최종 log 상태:

| Metric | 값 |
| --- | ---: |
| notification log count | 10,000 |
| sent log count | 10,000 |
| pending log count | 0 |
| failed log count | 0 |
| total attempt count | 20,000 |
| min attempt count | 2 |
| max attempt count | 2 |

Attempt 분포:

| Provider | Status | Attempt number | Count |
| --- | --- | ---: | ---: |
| `MOCK_EMAIL` | `FAILED` | 1 | 10,000 |
| `MOCK_EMAIL` | `SENT` | 2 | 10,000 |

실패 후 retry 성공:

| Metric | 값 |
| --- | ---: |
| failed then sent log count | 10,000 |

중복 검증:

| 검증 항목 | 결과 |
| --- | --- |
| duplicate notification log | 없음 |
| duplicate attempt number | 없음 |

해석:

- 모든 log가 1회 실패 후 1회 retry 성공으로 마무리됐다.
- 각 log의 attempt count는 정확히 2다.
- 실패 attempt와 성공 attempt가 같은 log 아래에 순서대로 기록됐다.
- retry 후 `PENDING` 또는 최종 `FAILED` 상태로 남은 log가 없다.
- 대량 retry에서도 중복 log와 중복 attempt number가 발생하지 않았다.

## 결론

Deadline reminder batch는 mock provider 기준 10,000개 후보를 중복 없이 처리했다.

확인한 품질:

- 대량 due-soon 후보 성공 처리
- 실패 attempt 기록
- retry window 기반 재처리
- retry 성공 후 상태 전이
- `notification_logs` 중복 0건
- `notification_attempts` 중복 0건
- 실패 후 retry 성공 log 10,000건

실제 provider 연동은 Mailgun 1건 smoke에서 검증했고, 대량 배치 품질은 mock provider로 분리해 검증했다. 이 분리는 실제 이메일 provider 비용과 계정 리스크를 피하면서도 batch idempotency와 retry/audit 품질을 수치로 남기기 위한 선택이다.

## 후속 검증 방향

- 더 큰 synthetic fixture에서 batch duration 측정
- 실패율이 일부만 섞인 mixed provider response smoke
- Redis key count 변화 측정
- scheduler lock 또는 multi-instance 실행 상황의 중복 방지 검증
