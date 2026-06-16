# Deadline Reminder Mailgun Smoke Report

## 목적

마감 알림 이메일 배치가 실제 Mailgun provider까지 연결되고, 발송 이력을 DB에 남기는지 확인한다.

검증 범위:

- 저장한 공고 중 24시간 이내 마감 대상 조회
- Redis SETNX 기반 중복 처리 guard
- Mailgun 실제 발송
- `notification_logs` 발송 단위 상태 기록
- `notification_attempts` 발송 시도 이력 기록
- 실패 재시도 대상 조회

## 측정 기준

| 항목 | 값 |
| --- | --- |
| 측정일 | `2026-06-16` |
| DB | local MySQL `jobflow` |
| 실행 방식 | `ApplicationRunner` non-web bootRun |
| Provider | Mailgun sandbox |
| Runner mode | `due-soon`, `retry` |
| Fixture SQL | `performance/sql/deadline-reminder-smoke-fixture.sql` |
| Check SQL | `performance/sql/deadline-reminder-smoke-check.sql` |
| Smoke script | `performance/notification/deadline-reminder-batch-smoke.sh` |

민감정보는 기록하지 않는다.

- `MAILGUN_API_KEY` 값은 기록하지 않는다.
- Mailgun sandbox domain, provider message id 원문은 공개 metrics 문서에 기록하지 않는다.
- 실제 수신자 이메일은 공개 metrics 문서에 기록하지 않는다.

## Fixture 주의사항

초기 smoke에서 DB `NOW()` 기준으로 `deadline_at = NOW() + 3 hours`를 넣었을 때, Spring app clock 기준으로는 과거 시간이 되어 공고가 `EXPIRED` 처리되는 문제가 있었다.

원인:

- Spring app은 Asia/Seoul local time 기준으로 `LocalDateTime.now(clock)`를 사용한다.
- MySQL session/server timezone의 `NOW()`가 app clock과 다를 수 있다.
- `JobExpirationScheduler`가 app clock 기준 과거 deadline을 `EXPIRED`로 전환한다.

해결:

- fixture SQL은 `UTC_TIMESTAMP(6) + 12 hours`로 `deadline_at`을 만든다.
- check SQL은 `@app_now = UTC_TIMESTAMP(6) + 9 hours` 기준으로 후보 여부를 확인한다.

## 실행 명령

Mailgun sandbox에서는 수신자 이메일이 Authorized Recipient에 등록되어 있어야 한다.

```bash
CONFIRM_EMAIL_SEND=true \
MAILGUN_DOMAIN='sandbox domain' \
MAILGUN_API_KEY='hidden' \
MAILGUN_FROM='postmaster@sandbox domain' \
MODE=due-soon \
bash performance/notification/deadline-reminder-batch-smoke.sh
```

재시도 smoke:

```bash
CONFIRM_EMAIL_SEND=true \
MAILGUN_DOMAIN='sandbox domain' \
MAILGUN_API_KEY='hidden' \
MAILGUN_FROM='postmaster@sandbox domain' \
MODE=retry \
bash performance/notification/deadline-reminder-batch-smoke.sh
```

## Smoke 결과

### 신규 마감 알림 발송

| Metric | 값 |
| --- | ---: |
| targetCount | 1 |
| sentCount | 1 |
| failedCount | 0 |
| skippedCount | 0 |

해석:

- 24시간 이내 마감되는 저장 공고 1건을 찾았다.
- Redis SETNX guard를 통과했다.
- Mailgun 발송에 성공했다.
- 실패나 skip 없이 1건 발송했다.

### 재시도

| Metric | 값 |
| --- | ---: |
| targetCount | 0 |
| sentCount | 0 |
| failedCount | 0 |
| skippedCount | 0 |

해석:

- 신규 발송이 성공해 `PENDING` 재시도 대상이 없었다.
- retry runner가 빈 대상에서도 정상 종료됨을 확인했다.

## DB 검증 결과

최근 마감 알림 row:

| 항목 | 값 |
| --- | --- |
| notification log status | `SENT` |
| attempt count | `1` |
| sent_at | present |
| attempt number | `1` |
| attempt status | `SENT` |
| provider | `MAILGUN` |
| provider message id | present |
| failure reason | empty |

중복 검증:

| 검증 항목 | 결과 |
| --- | --- |
| duplicate notification log | 없음 |
| duplicate attempt number | 없음 |

## 결론

Deadline reminder batch는 실제 Mailgun provider 발송, 발송 단위 상태 기록, 발송 시도 이력 기록까지 end-to-end smoke를 통과했다.

이번 smoke는 실제 provider 연동 확인 목적이므로 1건만 발송했다. 대량 이메일 배치 성능은 실제 Mailgun 대량 발송이 아니라 mock provider 기반 load test로 분리하는 것이 안전하다.

## 후속 검증 방향

- mock email provider 기반 대량 배치 처리량 측정
- 실패율이 섞인 provider 응답에서 retry/backoff 동작 확인
- Redis SETNX와 DB unique constraint의 중복 방지 결과 측정
- `notification_logs` / `notification_attempts` 기반 발송 성공률, 실패율, 재시도율 집계
