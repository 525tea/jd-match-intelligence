# W4-14 Real Job Analytics Quality Report

## 목적

JUMPIT/WANTED 실제 공고 300건 이상을 수집한 뒤, 정규화 품질, Analytics Batch 집계, Trend API 응답까지 연결되는지 확인한다.

이번 리포트는 수집 smoke가 아니라 다음 질문에 답하기 위한 품질 산출물이다.

- 실제 공고에 skill/tag/role/career/location/deadline이 분석 가능한 수준으로 붙는가?
- Analytics Batch가 실공고 데이터로 월별 트렌드/공동출현/시장 통계를 생성하는가?
- Trend API가 실공고 기반 집계 결과를 정상 반환하는가?
- 아직 해결되지 않은 source 한계와 후속 작업은 무엇인가?

## 측정 기준

| 항목 | 값 |
| --- | --- |
| 측정일 | 2026-06-10 |
| 대상 source | JUMPIT, WANTED |
| DB | local MySQL `jobflow` |
| 집계 월 | 2026-06-01 |
| 주요 SQL | `real-job-quality-gate.sql`, `analytics-market-aggregation-check.sql` |
| API smoke | `performance/analytics/analytics-trend-api-smoke.sh` |

## 수집 결과

| source | unique job count | note |
| --- | ---: | --- |
| JUMPIT | 150 | 추가 수집 runner log: processed 153, collected 150, failed 3 |
| WANTED | 155 | 추가 수집 runner log: processed 150, collected 150, failed 0 |
| 합계 | 305 | W4-14 목표 300~500건 충족 |

runner의 `collectedCount`는 created/updated를 모두 포함할 수 있으므로 최종 판단은 DB unique row 기준으로 한다.

## Quality Gate 결과

| source | job_count | zero_skill_rate | zero_experience_tag_rate | etc_role_rate | any_career_level_rate | missing_deadline_rate | missing_location_region_rate |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| JUMPIT | 150 | 2.67% | 16.00% | 2.00% | 0.00% | 0.00% | 8.00% |
| WANTED | 155 | 2.58% | 0.65% | 0.00% | 14.19% | 72.26% | 0.00% |

### PASS

- JUMPIT/WANTED 모두 필수 식별 필드는 누락되지 않았다.
  - `missing_external_id_count=0`
  - `missing_canonical_fingerprint_count=0`
  - `missing_title_count=0`
  - `missing_company_name_count=0`
  - `missing_original_url_count=0`
- JUMPIT/WANTED 모두 skill coverage가 PASS 기준이다.
- JUMPIT/WANTED 모두 experience tag coverage가 PASS 기준이다.
- JUMPIT/WANTED 모두 role ETC 비율이 PASS 기준이다.
- WANTED parser는 raw `due_time`이 있는 deadline을 놓치지 않았다.
  - `wanted_parser_missed_deadline_count=0`

### FAIL / 리스크

| source | metric | value | 판단 |
| --- | --- | ---: | --- |
| WANTED | `missing_deadline_rate` | 72.26% | FAIL |
| WANTED | `wanted_raw_due_time_missing_rate` | 72.26% | FAIL |

WANTED deadline 누락은 parser bug가 아니라 raw JSON의 `job.due_time` 자체가 없는 source-limited risk로 판단한다.

## Skill Trend 결과

상위 skill trend:

| rank | skill | job_count | trend_score |
| ---: | --- | ---: | ---: |
| 1 | Python | 126 | 252.0000 |
| 2 | Software Engineering | 92 | 184.0000 |
| 3 | C++ | 89 | 178.0000 |
| 4 | AWS | 87 | 174.0000 |
| 5 | Linux | 83 | 166.0000 |
| 6 | C | 74 | 148.0000 |
| 7 | Git | 73 | 146.0000 |
| 8 | Docker | 61 | 122.0000 |
| 9 | React | 59 | 118.0000 |
| 10 | Java | 57 | 114.0000 |

실제 JUMPIT/WANTED 공고에서 Python, C/C++, Linux, AWS, Docker, React, Java 계열이 상위로 집계됐다.

## Co-occurrence 결과와 리스크

Java 기준 co-occurrence API는 정상 응답했다.

예시:

| base | co_skill | cooccurrence_count | lift_score |
| --- | --- | ---: | ---: |
| Java | Spring Security | 3 | 5.5088 |
| Java | QueryDSL | 2 | 5.5088 |
| Java | Oracle Database | 9 | 4.5072 |
| Java | Apache HTTP Server | 8 | 4.4070 |

다만 전체 co-occurrence 상위에는 `cooccurrence_count=1`인 rare pair가 lift 상위로 튀는 현상이 있다.

예시:

- `Spectrum Analyzer - Network Analyzer`
- `Objective-C - Clean Architecture`
- `JMeter - k6`

후속 작업:

- W4-18 검색 query expansion에서는 `cooccurrence_count >= 3` 같은 minimum support filter를 적용한다.
- Precision@K before/after 비교 없이 co-occurrence expansion을 검색에 바로 반영하지 않는다.

## Job Market Stats 결과

Batch 수정 후 `job_market_stats`는 정상 집계됐다.

| metric | value |
| --- | ---: |
| `job_market_stats_count` | 143 |
| `summed_job_count` | 314 |
| `max_group_job_count` | 15 |

Role별 합산:

| role | total_job_count |
| --- | ---: |
| BACKEND | 70 |
| ROBOT_SOFTWARE | 37 |
| EMBEDDED_SOFTWARE | 28 |
| FRONTEND | 23 |
| SECURITY | 22 |
| PM | 19 |
| SOFTWARE_ENGINEER | 19 |
| AI_ENGINEER | 17 |
| DEVOPS | 17 |
| HARDWARE_ENGINEER | 11 |

`summed_job_count=314`가 unique job count 305보다 큰 이유는 기존 6월 mock/seed 데이터와 이번 실공고 수집 데이터가 같은 월 기준으로 함께 집계되었기 때문이다.

## Trend API Smoke 결과

검증 대상:

- `GET /trends/skills`
- `GET /trends/skills/{skillId}/cooccurrences`
- `GET /trends/skills/{skillId}/experience-tags`
- `GET /trends/market`

결과:

- 모든 endpoint가 `success=true`로 응답했다.
- `/trends/skills`는 실공고 기반 상위 skill trend를 반환했다.
- `/trends/market`은 BACKEND 시장 통계를 15/12/7건 단위로 반환했다.
- console 출력 중 일부 JSON 문자열이 깨져 보였으나 API 응답 구조와 데이터는 정상으로 확인했다.

## 발견한 결함과 수정

### `job_market_stats` 재집계 unique 충돌

증상:

```text
Duplicate entry 'MONTHLY-2026-06-01-BACKEND-ANY-Seoul-ONSITE'
for key 'job_market_stats.uk_job_market_stats_dimension'
```

원인:

- 기존 월별 통계를 삭제한 뒤 같은 transaction에서 신규 통계를 저장했다.
- delete SQL이 insert 전에 flush되지 않아 unique key가 충돌했다.

수정:

- `JobMarketStatsAggregationService`에서 delete 후 `jobMarketStatsRepository.flush()`를 호출한다.
- 같은 월 재집계 회귀 테스트를 추가했다.

### WANTED 경력 범위 파싱 이상치

증상:

```text
WANTED 367410
AI 에이전트 및 백엔드 엔지니어
min_experience_years=4
max_experience_years=2023
```

원문:

```text
경력: 관련 백엔드 또는 AI 엔지니어링 경력 3년 ~ 7년
```

원인:

- 기존 regex가 `"3년 ~ 7년"`의 첫 숫자 뒤 `년`을 허용하지 않았다.
- 정상 범위 문법을 놓치면서 뒤쪽 숫자 조합이 경력값으로 오염됐다.

수정:

- WANTED parser가 `"N년 ~ M년"` 형태를 경력 범위로 인식하도록 보정했다.
- 비현실적 경력 숫자는 경력값으로 채택하지 않는 방어 가드를 추가했다.
- WANTED 367410 회귀 테스트를 추가했다.
- 기존 오염 데이터는 `min=3`, `max=7`로 보정했다.

## 열린 리스크와 후속 태스크

| 리스크 | 현재 판단 | 후속 태스크 |
| --- | --- | --- |
| WANTED deadline 누락 | raw `due_time` 부재로 인한 source-limited risk | WANTED 대체 deadline 후보 분석 또는 null ranking 정책 |
| Rare pair co-occurrence lift 과대평가 | lift 계산은 정상이나 support가 낮은 pair가 상위 노출 | W4-18 minimum support filter |
| Required/Preferred 미구분 | 현재 대부분 required로 집계됨 | W4-16 JD 섹션 기반 구분 |
| 대표성 source 부족 | JUMPIT/WANTED 중심으로 대기업/중견 대표성 제한 | SARAMIN API 승인 후 실제 수집 편입 |
| 일부 role/skill long-tail | 품질 gate는 PASS지만 500/1000건 확장 시 새 패턴 가능 | 품질 gate 반복 측정 |

## 최종 판단

W4-14 기준으로 JUMPIT/WANTED 실공고 데이터는 Gap Analysis와 Trend API의 입력 데이터로 사용할 수 있는 상태다.

단, WANTED deadline과 co-occurrence support filter는 후속 작업에서 명시적으로 다뤄야 한다.
