# Real Job Collection Quality Plan

## 목적

Gap Analysis, JD Matching, Trend API가 fixture가 아니라 실제 공고 데이터 위에서 동작하도록 하기 위해 실제 수집 공고의 품질 기준을 정의한다.

WANTED/JUMPIT 10건 smoke는 collector source가 동작한다는 신호일 뿐, Gap Analysis에 투입 가능한 데이터셋을 확보했다는 의미는 아니다.
Gap Analysis 전에 source별 품질과 대표성을 측정한다.

## 현재 source 판단

| source | 상태 | 판단 |
| --- | --- | --- |
| JUMPIT | 운영 후보 | 개발자 특화 공고 중심. 100건 이상 수집 품질 측정 필요 |
| WANTED | 운영 후보 | JSON API 기반으로 구조화 품질이 좋음. 공고량/기업군 편향 확인 필요 |
| SARAMIN | 분석 예정 | 공식 API 후보. 대규모 coverage와 중견/대기업 공고 확보 가능성 확인 필요 |
| JOBKOREA | 분석 예정 | robots 허용 범위와 공개 목록/상세 구조 확인 필요. 대기업/중견기업 coverage 보강 후보 |
| ZIGHANG | experimental | 코드는 유지하되 대량 수집 source로 확정하지 않음. sitemap에 비IT/외부 출처가 섞여 품질 편차가 큼 |

## 단계

### w4-8: JUMPIT/WANTED 100건 품질 측정

- JUMPIT/WANTED를 보수적 delay로 소량 수집한다.
- source별 저장 건수와 parser 품질을 측정한다.
- 수집 데이터가 Analytics Batch와 Trend API에 투입 가능한지 확인한다.

### w4-9: 사람인/잡코리아 source 분석

- 사람인 공식 API 사용 가능성과 필드 품질을 확인한다.
- 잡코리아 robots 허용 범위, 목록/상세 URL 구조, 필드 품질을 확인한다.
- 네카라쿠배/대기업/중견기업 공고 coverage를 보강할 수 있는지 판단한다.

### w4-10: 대표성 source collector 구현

- SARAMIN 또는 JOBKOREA 중 하나를 먼저 구현한다.
- discovery/parser/upsert/outbox/test를 추가한다.
- 실제 collector smoke를 수행한다.

### w4-11: 실제 공고 300~500건 품질 리포트

- JUMPIT/WANTED/대표성 source를 섞어 실제 공고 300~500건을 수집한다.
- Analytics Batch를 재실행한다.
- Trend API가 실제 수집 데이터 기준으로 응답하는지 확인한다.

## 측정 지표

| 지표 | 의미 |
| --- | --- |
| source별 저장 건수 | source mix와 수집 성공률 확인 |
| `skill_count=0` 비율 | JD skill normalization 품질 확인 |
| `experience_tag_count=0` 비율 | JD phrase/tag mapping 품질 확인 |
| `role=ETC` 비율 | 직군 분류 품질 확인 |
| `career_level=ANY` 비율 | 경력 파싱 품질 확인 |
| `deadline_at null` 비율 | 마감일 파싱 품질 확인 |
| 비개발 공고 유입률 | source filter 품질 확인 |
| duplicate candidate count | source 간 중복 후보 확인 |
| batch 재실행 성공 여부 | Analytics 집계 호환성 확인 |
| Trend API 응답 여부 | 조회 API 호환성 확인 |

## 통과 기준

초기 통과 기준은 보수적으로 둔다.

- collector 실행 중 403/429가 발생하지 않는다.
- source별 `processedCount`, `collectedCount`, `skippedCount`, `failedCount`를 기록한다.
- 저장된 공고에 `source`, `external_id`, `canonical_fingerprint`, `title`, `company_name`, `original_url`이 존재한다.
- 비개발 공고가 운영 후보 source에 섞이면 원인을 기록하고 source filter를 보강한다.
- `skill_count=0`, `experience_tag_count=0`, `role=ETC`, `career_level=ANY`, `deadline_at null` 비율을 기록한다.
- Analytics Batch가 실제 수집 데이터로 재실행된다.
- Trend API가 실제 수집 데이터 기반 응답을 반환한다.

## 해석 기준

- WANTED는 구조화 JSON 품질이 좋지만 공고량과 기업군 대표성에 한계가 있을 수 있다.
- JUMPIT은 개발자 특화 공고 품질은 좋지만 대기업/중견기업 coverage가 충분한지 확인해야 한다.
- 사람인/잡코리아는 구현 난도가 있더라도 대표성 보강 source로 분석해야 한다.
- ZIGHANG은 현재 운영 후보가 아니라 experimental source다. 재개하려면 IT 후보 확보 방식부터 다시 검증한다.
