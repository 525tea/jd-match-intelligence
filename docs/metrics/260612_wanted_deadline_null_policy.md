# WANTED Deadline Null Policy

## 목적

WANTED 공고의 `deadline_at IS NULL`을 parser bug가 아니라 source-limited risk로 보고, 서비스에서 어떻게 처리할지 정책을 고정한다.

WANTED raw JSON 기준으로 `job.due_time` 자체가 비어 있는 공고가 많다. 따라서 JobFlow는 source에 없는 deadline을 임의로 생성하지 않는다.

## 배경 수치

WANTED 실제 공고 품질 측정에서 deadline 누락률은 높게 유지된다.

| metric | value |
| --- | ---: |
| missing_deadline_rate | 72.26% |
| wanted_raw_due_time_missing_rate | 72.26% |
| wanted_parser_missed_deadline_count | 0 |

해석:

- `wanted_parser_missed_deadline_count = 0`이므로 parser가 놓친 deadline은 없다.
- deadline 누락은 raw JSON의 `job.due_time` 부재에서 온다.
- 임의 deadline을 생성하면 source truth가 아닌 값을 저장하게 된다.

## 정책

### 검색 Ranking

`deadline_at IS NULL` 공고는 검색에서 제외하지 않는다.

대신 마감 임박 boost 대상에서는 제외한다.

정책:

```text
deadline_at 존재
-> deadline proximity boost 적용

deadline_at NULL
-> deadline proximity boost 미적용
-> 텍스트 relevance, freshness, role/source 등 다른 ranking 신호로 경쟁
```

금지:

```text
deadline_at NULL -> 임의 deadline 생성
deadline_at NULL -> 과도한 penalty 부여
deadline_at NULL -> 검색 결과에서 제외
```

이유:

WANTED는 null deadline 비율이 높다. null deadline에 강한 penalty를 주면 WANTED 공고 대부분이 검색 ranking에서 밀려 source diversity가 깨진다.

### 만료 Scheduler

`deadline_at IS NULL`인 OPEN 공고는 자동 만료하지 않는다.

정책:

```text
status = OPEN AND deadline_at < now
-> EXPIRED 전환 대상

status = OPEN AND deadline_at IS NULL
-> EXPIRED 전환 대상 아님
```

이유:

deadline을 모르는 공고는 만료 기준 시각도 모른다. source에 없는 deadline을 추정해 만료 처리하면 실제로 아직 열려 있는 공고를 닫을 수 있다.

### 추천/JD 매칭 후속 정책

추천 시스템 구현 시 null deadline은 임의 freshness score로 보정하지 않는다.

기본 방향:

```text
deadline_at 존재
-> deadline/freshness 계열 점수 계산 가능

deadline_at NULL
-> deadline proximity 점수 없음
-> no deadline boost 또는 neutral 처리
```

실제 추천 점수 반영은 추천 시스템 구현 시점에 별도 테스트로 고정한다.

## 구현

### 검색

Elasticsearch function score의 deadline gauss function에 `exists(deadlineAt)` filter를 추가한다.

결과:

```text
deadlineAt이 있는 문서만 deadline proximity boost 대상
deadlineAt이 null인 문서는 deadline boost 없이 검색 참여
```

### 만료

`JobExpirationService`는 `JobRepository.findByStatusAndDeadlineAtBefore(OPEN, now)`를 사용한다.

SQL 비교에서 `deadline_at IS NULL`은 `deadline_at < now` 조건을 만족하지 않으므로 자동 만료 대상에서 제외된다.

통합 테스트에서 WANTED null deadline OPEN 공고가 `EXPIRED`로 바뀌지 않고, `JOB_EXPIRED` outbox event도 저장되지 않음을 확인한다.

## 검증

검색 ranking regression:

```bash
./gradlew :backend:test --tests jobflow.domain.job.search.ElasticsearchJobSearchServiceTest
```

만료 정책 regression:

```bash
./gradlew :backend:test \
  --tests jobflow.domain.job.JobExpirationRepositoryTest \
  --tests jobflow.domain.job.JobExpirationServiceTest \
  --tests jobflow.domain.job.JobExpirationServiceFlowTest
```

DB Console 확인:

```sql
-- performance/sql/wanted-deadline-null-policy-check.sql 실행
```

기대 결과:

```text
wanted_parser_missed_deadline_count = 0
expirable_null_deadline_count = 0
WANTED + OPEN + deadline_at IS NULL 샘플 존재 가능
```

## 결정

WANTED deadline null은 데이터 품질 실패가 아니라 source-limited risk로 관리한다.

JobFlow는 source에 없는 deadline을 만들지 않는다. 서비스 정책은 다음과 같다.

```text
검색: null deadline 공고는 검색 참여, deadline boost 제외
만료: null deadline 공고는 자동 만료 제외
추천: null deadline은 후속 추천 구현에서 no deadline boost 또는 neutral 처리
```

## 후속 작업

- 추천 시스템 구현 시 null deadline freshness 점수 정책을 테스트로 고정한다.
- SARAMIN/JOBKOREA 등 deadline coverage가 높은 source가 추가되면 source mix 기준으로 deadline coverage를 다시 측정한다.
- WANTED API 응답에 deadline 대체 필드가 생기면 `wanted_parser_missed_deadline_count`로 parser 개선 여부를 다시 판단한다.
