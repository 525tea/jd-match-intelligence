# WANTED Detail Parsing Quality Report

작성일: 2026-06-22  
범위: WANTED 공고 상세 파싱, 직무 분류, raw snapshot replay 검증

## 목적

WANTED 공고 상세 화면에서 확인된 파싱/분류 회귀를 수정하고, 기존 수집 데이터 155건에 대해 raw snapshot replay backfill을 수행해 데이터 품질이 회복됐는지 검증했다.

대표 회귀 케이스는 다음과 같다.

- `Data Engineer` 계열 공고가 `FRONTEND`로 분류되는 문제
- WANTED 원문 단어 내부 줄바꿈으로 `anti-bot`, `N-layer` 계열 문장이 깨지는 문제
- 문장 중간 `·/ㆍ/﹒` 기호가 bullet 항목으로 오인되는 문제
- backend section parser가 이미 조립된 WANTED description을 다시 잘못 분리하는 문제

## 원인

수집 파이프라인 자체는 정상적으로 동작했다. 문제는 수집 이후 파싱/분류/display 레이어에서 발생했다.

```text
수집 성공
  -> WANTED source-specific parser
  -> description flat string 저장
  -> backend JobDescriptionSectionParser 재파싱
  -> 직무 분류 및 상세 렌더링 오류
```

주요 원인은 세 가지였다.

1. WANTED 원문에 포함된 단어 내부 줄바꿈을 collector parser에서 복구하지 못했다.
2. backend `JobDescriptionSectionParser`가 문장 중간 middle dot을 bullet marker로 처리했다.
3. `JdJobRoleClassificationService`가 title의 명확한 `Data Engineer` 신호보다 본문 키워드를 더 강하게 반영했다.

## 수정 요약

### WANTED word-internal line break 복구

collector의 WANTED parser에서 영문 단어 내부 줄바꿈을 복구하도록 했다.

예시:

```text
anti
bot -> antibot

N
layer -> Nlayer
```

### Inline middle dot 보존

backend section parser는 줄 시작의 `·/ㆍ/﹒`만 bullet로 취급하도록 변경했다.

문장 중간의 `CS · 관제 · 고객` 같은 표현은 그대로 보존한다.

### Role title-first 규칙 보강

`Data Engineer`, `데이터 엔지니어`, `ETL`, `데이터 파이프라인` 등 제목에 명확한 데이터 엔지니어 신호가 있으면 본문 분석보다 먼저 `DATA_ENGINEER`로 분류하도록 했다.

### Raw snapshot replay backfill 보정

`jobs.raw_data`가 purge된 이후에도 snapshot 파일에서 원문을 다시 읽어 description/role/skill/experience tag를 재생성할 수 있도록 replay 스크립트의 snapshot root path를 명시했다.

## Backfill 결과

실행 명령:

```bash
SOURCES=WANTED \
bash performance/collector/backfill-raw-job-description-replay.sh
```

결과:

```text
Raw job description replay backfill completed.
sources=[WANTED]
processedCount=155
updatedDescriptionCount=0
unchangedDescriptionCount=155
updatedRoleCount=55
skippedCount=0
failedCount=0
normalizedSkillJobCount=142
normalizedExperienceTagJobCount=154
```

해석:

- WANTED 155건 모두 snapshot에서 정상 replay됐다.
- skip/fail 없이 전체 처리됐다.
- description text는 이미 최신 형태와 동일했다.
- role 55건이 최신 title-first/classifier 규칙에 맞게 갱신됐다.
- skill/experience tag normalization도 정상 재적용됐다.

## 품질 게이트 결과

실행 SQL:

```sql
SOURCE performance/sql/wanted-detail-parsing-quality-check.sql;
```

요약 결과:

```text
check_name: WANTED_DETAIL_PARSING_SUMMARY
wanted_job_count: 155
data_engineer_role_mismatch_count: 0
word_internal_break_suspect_count: 0
inline_middle_dot_bullet_suspect_count: 0
missing_snapshot_metadata_count: 0
```

상세 문제 샘플 쿼리 결과:

```text
ROLE_MISMATCH: 0 rows
WORD_INTERNAL_BREAK_SUSPECT: 0 rows
INLINE_MIDDLE_DOT_BULLET_SUSPECT: 0 rows
```

## 검증 기준

| 항목 | 기대값 | 결과 |
|---|---:|---:|
| WANTED 공고 수 | 155 | 155 |
| Data Engineer role mismatch | 0 | 0 |
| word-internal break suspect | 0 | 0 |
| inline middle dot bullet suspect | 0 | 0 |
| missing snapshot metadata | 0 | 0 |
| replay skipped count | 0 | 0 |
| replay failed count | 0 | 0 |

## 결론

WANTED 상세 파싱과 직무 분류 회귀는 데이터 기준으로 해소됐다.

이번 작업으로 다음 품질 기준을 만족한다.

- `Data Engineer` 계열 공고는 `DATA_ENGINEER`로 분류된다.
- WANTED 원문 내 단어 내부 줄바꿈이 상세 화면에서 깨진 bullet로 노출되지 않는다.
- 문장 중간 middle dot은 항목 구분자로 오인되지 않는다.
- raw snapshot 기반 replay backfill이 purge 이후 데이터 복구 경로로 동작한다.
