
# Real Job Collection Quality Report

## 목적

JUMPIT/WANTED 실제 공고 수집 결과가 Gap Analysis, JD Matching, Trend API에 사용할 수 있는 품질인지 확인한다.

이번 작업은 collector가 단순히 실행되는지 보는 smoke가 아니라, 저장된 공고가 분석 가능한 구조화 데이터인지 확인하는 품질 게이트다.

## 측정 대상

| 항목 | 값 |
| --- | --- |
| 대상 source | JUMPIT, WANTED |
| 측정 DB | local MySQL `jobflow` |
| 측정 기준 | `jobs`, `job_skills`, `job_experience_tags` |
| 측정 쿼리 | `performance/sql/real-job-quality-measurement.sql` |

## 1차 SQL Smoke 결과

아래 결과는 품질 측정 SQL이 정상 동작하는지 확인하기 위한 소량 smoke다.
표본 수가 작기 때문에 최종 source 품질 판단에는 사용하지 않는다.

| source | job_count | zero_skill_rate | zero_experience_tag_rate | etc_role_rate | any_career_level_rate | missing_deadline_rate | missing_location_region_rate |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| JUMPIT | 5 | 20.00% | 60.00% | 0.00% | 0.00% | 0.00% | 0.00% |
| WANTED | 10 | 10.00% | 40.00% | 0.00% | 20.00% | 70.00% | 0.00% |

## 1차 해석

### 양호한 지표

- JUMPIT/WANTED 모두 `external_id`, `canonical_fingerprint`, `title`, `company_name`, `original_url` 누락이 없었다.
- `role=ETC` 비율이 0%로, 직군 분류는 소량 smoke 기준 안정적이었다.
- `location_region` 누락이 0%로, 지역 필드는 소량 smoke 기준 안정적이었다.

### 보강 후보

- `experience_tag_count=0` 비율이 높다.
  - JUMPIT: 60.00%
  - WANTED: 40.00%
- WANTED의 `deadline_at` 누락 비율이 높다.
  - WANTED: 70.00%
- WANTED의 `career_level=ANY` 비율이 일부 존재한다.
  - WANTED: 20.00%

## 지표 의미

| 지표 | 의미 |
| --- | --- |
| `missing_external_id_count` | `source + external_id` 기반 중복 저장 방지 키가 비어 있는 공고 수 |
| `missing_canonical_fingerprint_count` | 사이트 간 중복 후보 탐지용 fingerprint가 비어 있는 공고 수 |
| `zero_skill_rate` | 정규화된 skill이 하나도 붙지 않은 공고 비율 |
| `zero_experience_tag_rate` | JD 경험 태그가 하나도 붙지 않은 공고 비율 |
| `etc_role_rate` | 직군이 `ETC`로 분류된 공고 비율 |
| `any_career_level_rate` | 경력 수준이 `ANY`로 분류된 공고 비율 |
| `missing_deadline_rate` | 마감일이 없는 공고 비율 |
| `missing_location_region_rate` | 지역 정보가 없는 공고 비율 |

## 100건 품질 측정 계획

다음 단계에서는 JUMPIT/WANTED를 합산 100건 내외로 확장 수집한 뒤 같은 쿼리로 품질을 다시 측정한다.

확인할 항목:

- source별 저장 건수
- `zero_skill_rate`
- `zero_experience_tag_rate`
- `etc_role_rate`
- `any_career_level_rate`
- `missing_deadline_rate`
- `missing_location_region_rate`
- source 내부 중복 여부
- source 간 `canonical_fingerprint` 중복 후보
- 비개발 공고 유입 여부

## 통과 판단 기준

초기 기준은 보수적으로 둔다.

| 지표 | 기준 |
| --- | --- |
| 필수 필드 누락 | 0% 유지 |
| `role=ETC` | 낮을수록 좋음. 100건 기준 급증 시 role classifier 보강 |
| `zero_skill_rate` | 10~20% 이하 목표 |
| `zero_experience_tag_rate` | 높을 수 있으나 원인 분석 필요. mapping 부족이면 보강 |
| `deadline_at null` | source 특성인지 parser 누락인지 구분 |
| 비개발 공고 유입 | 0건 목표 |

## 후속 판단

- JUMPIT/WANTED만으로 공고량과 기업군 대표성이 부족하면 SARAMIN/JOBKOREA source 분석을 진행한다.
- `experience_tag_count=0` 비율이 높게 유지되면 `jd_phrase_tag_mapping`을 보강한다.
- WANTED `deadline_at` 누락이 source 특성이면 품질 리포트에 명시하고, 마감일 ranking에서는 null 처리 정책을 별도로 둔다.
- 100건 품질 측정 통과 후 실제 공고 300~500건 수집과 Analytics Batch 재실행으로 넘어간다.

## 200건 품질 측정 결과

W4-8에서는 JUMPIT/WANTED를 각 100건씩 수집해 총 200건 기준으로 quality gate를 실행했다.

| source | job_count | zero_skill_rate | zero_experience_tag_rate | etc_role_rate | any_career_level_rate | missing_deadline_rate | missing_location_region_rate |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| JUMPIT | 100 | 25.00% | 59.00% | 0.00% | 0.00% | 0.00% | 7.00% |
| WANTED | 100 | 7.00% | 25.00% | 0.00% | 15.00% | 72.00% | 0.00% |

### Quality Gate 판정

| source | metric | value | status | 해석 |
| --- | --- | ---: | --- | --- |
| JUMPIT | `zero_skill_rate` | 25.00% | WARN | skill alias/seed 보강 필요 |
| JUMPIT | `zero_experience_tag_rate` | 59.00% | WARN | phrase mapping 보강 필요. FAIL 기준 60%에 근접 |
| JUMPIT | `etc_role_rate` | 0.00% | PASS | role taxonomy 확장 후 안정화 |
| JUMPIT | `missing_deadline_rate` | 0.00% | PASS | deadline 파싱 안정 |
| WANTED | `zero_skill_rate` | 7.00% | PASS | skill tag 기반 정규화 양호 |
| WANTED | `zero_experience_tag_rate` | 25.00% | PASS | 현재 기준 통과 |
| WANTED | `etc_role_rate` | 0.00% | PASS | role taxonomy 확장 후 안정화 |
| WANTED | `missing_deadline_rate` | 72.00% | FAIL | raw JSON 원천 누락인지 parser 누락인지 분리 필요 |

필수 필드 계열은 JUMPIT/WANTED 모두 PASS였다.

- `missing_external_id_count=0`
- `missing_canonical_fingerprint_count=0`
- `missing_title_count=0`
- `missing_company_name_count=0`
- `missing_original_url_count=0`

## Role Validation 결과

Role 품질은 단순히 `ETC` 비율만으로 판단하지 않고, 실제 공고 샘플에 expected role을 라벨링한 fixture와 비교했다.

검증 SQL:

- `performance/sql/real-job-role-validation-fixture.sql`
- `performance/sql/real-job-role-validation-check.sql`

최종 결과:

| 항목 | 결과 |
| --- | ---: |
| labeled sample | 20건 |
| mismatch | 0건 |
| JUMPIT `etc_role_rate` | 0.00% |
| WANTED `etc_role_rate` | 0.00% |

Role taxonomy 보강으로 다음 계열이 `ETC`가 아니라 별도 role로 분류된다.

- `EMBEDDED_SOFTWARE`
- `ROBOT_SOFTWARE`
- `HARDWARE_ENGINEER`
- `ERP_SAP`
- `SYSTEM_SOFTWARE`
- `SOFTWARE_ENGINEER`
- AI/Data/Game 세부 role

## Backfill 검증

정규화 로직을 고친 뒤 외부 source를 재수집하지 않고 real job normalization backfill을 실행했다.

최종 backfill 결과:

```text
Real job normalization backfill completed.
sources=[JUMPIT, WANTED],
processedCount=200,
roleUpdatedCount=136,
normalizedSkillJobCount=168,
normalizedExperienceTagJobCount=116
```

이 결과는 raw data 보존과 backfill 루프가 실제로 동작한다는 근거다.

## Skill Alias 보강 결과

실제 공고 zero-skill 샘플을 기준으로 long-tail skill seed와 alias를 보강했다.

보강 범위:

- Embedded/robotics: `C`, `C++`, `RTOS`, `Firmware`, `ROS`, `ROS2`, `FPGA`, `PLC`
- Web/database/tool: `Node`, `React.js`, `Postgres`, `SAP`, `ERP`, `S/W`
- Security/network/hardware: `Network`, `TCP/IP`, `BGP`, `ISMS`, `CISSP`, `RF`, `Spectrum Analyzer`

Backfill 결과:

```text
Real job normalization backfill completed.
sources=[JUMPIT, WANTED],
processedCount=200,
roleUpdatedCount=0,
normalizedSkillJobCount=198,
normalizedExperienceTagJobCount=116
```

개선 전후:

| source | before zero_skill_rate | after zero_skill_rate | status |
| --- | ---: | ---: | --- |
| JUMPIT | 25.00% | 0.00% | PASS |
| WANTED | 7.00% | 2.00% | PASS |

전체 기준 skill이 하나 이상 붙은 공고는 `168/200`에서 `198/200`으로 개선됐다.

남은 WANTED zero-skill 2건은 설명이 회사 소개와 비즈니스 도메인 중심이고 명확한 기술 스택 표현이 부족했다.
따라서 이번 작업에서는 억지 alias를 추가하지 않고, skill normalization 오염을 피하는 쪽으로 판단했다.

### CI 회귀 수정

`RealJobSkillAliasSeedTest`가 테스트 DB의 `skills` table을 직접 삭제하면서 전체 backend test에서 `job_skills.skill_id -> skills.id` FK와 충돌했다.

수정 후에는 table 삭제 없이 테스트 setup에서 `V2 -> V9 -> V10` seed SQL만 idempotent하게 적용한다.
test profile은 `flyway.enabled=false`, `ddl-auto=create-drop`이므로 migration DDL의 default가 없는 컬럼은 테스트 setup에서 기본값을 보정한다.

검증:

```text
./gradlew :backend:test
BUILD SUCCESSFUL
```

## W4-9 시점 판단

W4-9 기준으로 실제 공고 수집/정규화 파이프라인은 다음 상태다.

- JUMPIT/WANTED는 필수 필드 누락 없이 저장된다.
- role taxonomy와 title-first 분류 보정으로 role validation mismatch 0을 달성했다.
- JUMPIT skill coverage는 alias/seed 보강 후 PASS 기준까지 개선됐다.
- WANTED skill coverage도 PASS 기준까지 개선됐다.
- JUMPIT experience tag coverage는 아직 부족하다.
- WANTED deadline은 FAIL이며 원천 데이터 유무를 별도 분석해야 한다.

따라서 다음 작업은 source 확장이 아니라 품질 개선 루프다.

1. W4-10: zero-experience-tag 샘플 기반 phrase mapping 보강
2. W4-11: WANTED deadline raw JSON 분석

## Experience Tag Phrase 보강 결과

W4-10에서는 실제 공고 200건 중 `experience_tag_count=0`인 공고를 분석하고, 반복 등장하는 JD phrase를 `jd_phrase_tag_mapping`에 보강했다.

분석 SQL:

- `performance/sql/real-job-zero-experience-tag-analysis.sql`

보강 migration:

- `V11__add_real_job_experience_tag_phrases.sql`

주요 보강 범위:

- `PERFORMANCE`: `최적화`, `고도화`, `성능 검증`, `성능 개선`, `알고리즘 최적화`, `추론 성능`
- `TESTING`: `테스트`, `검증`, `시뮬레이션`, `품질 검증`, `테스트 케이스`
- `RELIABILITY`: `유지보수`, `기술 지원`, `운영 경험`, `문제 해결`, `이슈 대응`
- `CLOUD_INFRA`: `인프라`, `환경 구축`, `시스템 통합`, `데이터 파이프라인`, `파이프라인 구축`
- `CI_CD`: `배포`, `서비스 배포`, `모델 배포`, `파이프라인 운영`
- `SECURITY`: `보안 요구사항`, `인증 대응`, `위협 모델링`, `위험 평가`

Backfill 결과:

```text
Real job normalization backfill completed.
sources=[JUMPIT, WANTED],
processedCount=200,
roleUpdatedCount=0,
normalizedSkillJobCount=198,
normalizedExperienceTagJobCount=186
```

개선 전후:

| source | before zero_experience_tag_rate | after zero_experience_tag_rate | status |
| --- | ---: | ---: | --- |
| JUMPIT | 59.00% | 14.00% | PASS |
| WANTED | 25.00% | 0.00% | PASS |

최종 quality gate:

| source | metric | value | status |
| --- | --- | ---: | --- |
| JUMPIT | `zero_skill_rate` | 0.00% | PASS |
| JUMPIT | `zero_experience_tag_rate` | 14.00% | PASS |
| JUMPIT | `etc_role_rate` | 0.00% | PASS |
| JUMPIT | `missing_deadline_rate` | 0.00% | PASS |
| WANTED | `zero_skill_rate` | 2.00% | PASS |
| WANTED | `zero_experience_tag_rate` | 0.00% | PASS |
| WANTED | `etc_role_rate` | 0.00% | PASS |
| WANTED | `missing_deadline_rate` | 72.00% | FAIL |

남은 JUMPIT zero-tag 14건은 NVR/FW, ROS 주행 SW, 안테나 측정 등 현재 9개 experience tag taxonomy에 억지로 매핑하기 어려운 공고가 대부분이다.
`개발 경험`, `구현 경험`, `설계 경험` 같은 넓은 phrase를 추가하면 대부분의 공고에 tag가 붙어 quality gate 수치는 좋아지지만 JD Matching과 Gap Analysis 근거가 오염될 수 있다.
따라서 이번 작업에서는 `14.00% PASS`에서 멈추고, 남은 FAIL인 WANTED deadline 분석을 다음 작업으로 넘긴다.

W4-10 이후 실제 공고 수집/정규화 파이프라인의 열린 리스크는 다음 하나로 좁혀졌다.

1. W4-11: WANTED `missing_deadline_rate=72.00%` 원천 누락/파싱 누락 분리 분석

## WANTED Deadline Raw 분석 결과

W4-11에서는 WANTED `missing_deadline_rate=72.00%`가 parser 누락인지, source raw JSON 원천 누락인지 분리했다.

분석 SQL:

- `performance/sql/wanted-deadline-raw-analysis.sql`
- `performance/sql/real-job-quality-gate.sql`

분석 대상:

| 항목 | 값 |
| --- | ---: |
| source | WANTED |
| job_count | 100 |
| missing_deadline_count | 72 |
| missing_deadline_rate | 72.00% |
| raw_due_time_present_count | 28 |
| raw_due_time_missing_count | 72 |

deadline 상태 분해:

| status | job_count | 해석 |
| --- | ---: | --- |
| `RAW_DUE_TIME_MISSING` | 72 | raw JSON의 `job.due_time` 값 자체가 `null` |
| `DEADLINE_PARSED` | 28 | raw JSON의 `job.due_time`이 있고 `deadline_at`으로 정상 파싱됨 |
| `PARSER_MISSED_DUE_TIME` | 0 | raw JSON에는 due_time이 있는데 parser가 놓친 케이스 없음 |

raw JSON key 확인 결과, WANTED 상세 JSON에는 `job.due_time` 키가 항상 존재하지만 72건은 값이 `null`이었다.
`deadline`, `close`, `end_time`, `until`, `period` 계열 대체 마감 필드도 현재 수집 raw JSON에서는 확인되지 않았다.

따라서 WANTED `missing_deadline_rate=72.00%`는 현재 기준 parser bug가 아니라 source-limited risk로 판단한다.

Quality gate에는 이 판단을 반복 검증할 수 있도록 WANTED 전용 지표를 추가했다.

| source | metric | value | status | 해석 |
| --- | --- | ---: | --- | --- |
| WANTED | `missing_deadline_rate` | 72.00% | FAIL | deadlineAt 누락 비율 자체는 여전히 높음 |
| WANTED | `wanted_raw_due_time_missing_rate` | 72.00% | FAIL | raw JSON의 `job.due_time`이 없는 비율과 동일 |
| WANTED | `wanted_parser_missed_deadline_count` | 0 | PASS | 파싱 가능한 `due_time`을 놓친 케이스 없음 |

후속 정책:

- WANTED deadline 누락은 parser 수정으로 해결할 수 있는 문제가 아니므로 임의 deadline을 생성하지 않는다.
- 검색 ranking, 만료 스케줄러, trend/market 분석에서는 `deadline_at IS NULL` 공고를 별도 정책으로 다룬다.
- source 확장 시 사람인/잡코리아에서 deadline coverage를 보강한다.
