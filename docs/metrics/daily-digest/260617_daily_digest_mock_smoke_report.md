# Daily Digest Mock Smoke Report

## 목적

Daily Digest 이메일 배치가 사용자별 최신 프로젝트를 기준으로 맞춤 공고 요약을 만들고, 중복 발송 없이 성공/실패/retry 흐름을 기록하는지 확인한다.

실제 이메일 대량 발송은 비용, provider 제한, 계정 평판 리스크가 있으므로 사용하지 않는다. 이번 검증은 mock email provider를 사용해 content 생성, user-level notification log, attempt audit, retry 흐름을 확인한다.

검증 범위:

- 사용자별 최신 프로젝트 선택
- 추천 공고, JD 매칭 공고, 신규 공고, 마감 임박 저장 공고를 포함한 Digest 생성
- mock email provider 기반 daily batch 성공 처리
- mock email provider 실패 모드 기반 실패 attempt 생성
- retry window 조정 후 retry mode 성공 처리
- `notification_logs` 중복 생성 여부 확인
- `notification_attempts` attempt number 중복 여부 확인
- 실패 후 재시도 성공 흐름 확인

## 측정 기준

| 항목 | 값 |
| --- | --- |
| 측정일 | `2026-06-17` |
| DB | local MySQL `jobflow` |
| 실행 방식 | `ApplicationRunner` non-web bootRun |
| Provider | Mock email provider |
| Runner mode | `daily`, `retry` |
| Target roles | `BACKEND`, `FULLSTACK` |
| Target career level | `MID` |
| Fixture SQL | `performance/sql/daily-digest-smoke-fixture.sql` |
| Success check SQL | `performance/sql/daily-digest-smoke-check.sql` |
| Retry 준비 SQL | `performance/sql/daily-digest-smoke-ready-retry.sql` |
| Retry check SQL | `performance/sql/daily-digest-smoke-retry-check.sql` |
| Smoke script | `performance/notification/daily-digest-mock-smoke.sh` |

민감정보는 기록하지 않는다.

- 실제 이메일 provider API key를 사용하지 않는다.
- 실제 수신자 이메일을 사용하지 않는다.
- 실제 provider domain이나 provider message id 원문을 기록하지 않는다.
- smoke fixture 사용자는 `daily-digest-smoke-user-%@example.com` 패턴으로 제한한다.

## Fixture

| Metric | 값 |
| --- | ---: |
| smoke user count | 2 |
| smoke project count | 3 |
| smoke analysis count | 3 |
| smoke project skill count | 18 |
| smoke project experience tag count | 9 |
| smoke job count | 4 |
| smoke job skill index count | 24 |
| smoke saved deadline count | 2 |

해석:

- 2명의 mock user를 만들었다.
- 한 사용자는 최신 프로젝트와 과거 프로젝트를 모두 갖고 있어, batch가 최신 프로젝트만 선택하는지 확인할 수 있다.
- 각 최신 프로젝트는 static analysis 결과, project skill, experience tag를 가진다.
- 공고 fixture는 추천 공고, JD 매칭 공고, 신규 공고, 마감 임박 저장 공고 흐름을 검증할 수 있게 구성했다.
- 마감 임박 공고는 두 mock user 모두 저장한 상태다.

## 성공 경로

실행:

```bash
MOCK_EMAIL_FAIL=false \
bash performance/notification/daily-digest-mock-smoke.sh
```

결과:

| Metric | 값 |
| --- | ---: |
| targetCount | 2 |
| sentCount | 2 |
| failedCount | 0 |
| skippedCount | 0 |

DB 검증:

| Metric | 값 |
| --- | ---: |
| notification log count | 2 |
| sent log count | 2 |
| pending log count | 0 |
| failed log count | 0 |
| total attempt count | 2 |
| min attempt count | 1 |
| max attempt count | 1 |
| duplicate notification log | 0 |
| duplicate attempt number | 0 |

해석:

- Daily Digest batch가 smoke user 2명을 대상으로 2건을 발송했다.
- 각 사용자마다 `notification_logs`가 1개씩 생성됐다.
- 각 log마다 `notification_attempts`가 1개씩 생성됐다.
- 모든 log가 `SENT` 상태가 됐다.
- duplicate notification log와 duplicate attempt number가 없다.

## 실패 경로

실행:

```bash
MOCK_EMAIL_FAIL=true \
bash performance/notification/daily-digest-mock-smoke.sh
```

결과:

| Metric | 값 |
| --- | ---: |
| targetCount | 2 |
| sentCount | 0 |
| failedCount | 2 |
| skippedCount | 0 |

해석:

- mock provider 실패 모드에서 2개 대상이 모두 실패 attempt로 기록됐다.
- 실패한 log는 retry 대상이 될 수 있도록 `PENDING` 상태와 `next_retry_at`을 가진다.
- 실패 시 Redis idempotency key는 release되어 이후 retry가 가능하다.

## Retry 준비

실행:

```sql
-- performance/sql/daily-digest-smoke-ready-retry.sql
```

결과:

| Metric | 값 |
| --- | ---: |
| retry ready notification count | 2 |

해석:

- 실패한 `PENDING` log 2건을 app clock 기준 retry 가능 window로 이동했다.

## Retry 성공 경로

실행:

```bash
MODE=retry \
MOCK_EMAIL_FAIL=false \
bash performance/notification/daily-digest-mock-smoke.sh
```

결과:

| Metric | 값 |
| --- | ---: |
| targetCount | 2 |
| sentCount | 2 |
| failedCount | 0 |
| skippedCount | 0 |

최종 log 상태:

| Metric | 값 |
| --- | ---: |
| notification log count | 2 |
| sent log count | 2 |
| pending log count | 0 |
| failed log count | 0 |
| total attempt count | 4 |
| min attempt count | 2 |
| max attempt count | 2 |

Attempt 분포:

| Provider | Status | Attempt number | Count |
| --- | --- | ---: | ---: |
| `MOCK_EMAIL` | `FAILED` | 1 | 2 |
| `MOCK_EMAIL` | `SENT` | 2 | 2 |

실패 후 retry 성공:

| Metric | 값 |
| --- | ---: |
| failed then sent log count | 2 |

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
- retry에서도 중복 log와 중복 attempt number가 발생하지 않았다.

## 격리 기준

Daily Digest smoke는 기존 로컬 사용자 데이터를 오염시키지 않기 위해 `targetUserEmailPattern`으로 대상 사용자를 제한한다.

| 항목 | 값 |
| --- | --- |
| smoke target user pattern | `daily-digest-smoke-user-%@example.com` |
| 운영 기본값 | 전체 사용자 대상 |
| smoke 기본값 | smoke fixture 사용자만 대상 |

해석:

- 운영에서는 별도 target user pattern을 설정하지 않으면 전체 사용자가 대상이다.
- smoke script는 fixture 사용자만 대상으로 삼아 로컬 DB에 남아 있는 다른 프로젝트가 발송 대상에 섞이지 않게 한다.

## 결론

Daily Digest batch는 mock provider 기준 사용자별 Digest 생성, 성공 발송, 실패 attempt 기록, retry 성공, 중복 방지 흐름을 정상 처리했다.

확인한 품질:

- 사용자별 최신 프로젝트 기준 Digest 대상 선정
- mock provider 성공 발송
- mock provider 실패 attempt 기록
- retry window 기반 재처리
- retry 성공 후 상태 전이
- `notification_logs` 중복 0건
- `notification_attempts` 중복 0건
- 실패 후 retry 성공 log 2건

이번 검증은 실제 provider 발송 품질이 아니라 Daily Digest batch의 orchestration, idempotency, audit log, retry 품질을 확인하기 위한 smoke다.

## 후속 검증 방향

- Daily Digest 실제 provider 1건 smoke
- 더 큰 synthetic fixture에서 batch duration 측정
- 실패율이 일부만 섞인 mixed provider response smoke
- Redis key count 변화 측정
- multi-instance 실행 상황의 중복 방지 검증
