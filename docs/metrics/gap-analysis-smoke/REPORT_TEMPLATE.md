# Gap Analysis API Smoke Report

## 목적

Gap Analysis API가 다음 데이터를 end-to-end로 연결하는지 확인한다.

- 최신 사용자 프로젝트 skill snapshot
- `job_skill_index` required/preferred skill index
- target role filtering
- 사용자 프로젝트 소유권 / not-found guard
- match score ranking
- matched/missing skill detail response

## 측정 기준

| 항목 | 값 |
| --- | --- |
| 측정일 | TODO |
| DB | local MySQL `jobflow` |
| API base URL | `http://localhost:8080` |
| User | smoke fixture user |
| Project external id | smoke fixture project |
| Target roles | `BACKEND,FULLSTACK,SOFTWARE_ENGINEER,DEVOPS` |
| Limit | `10` (`1..50`) |
| Missing project check | `true` |
| Missing project id | `999999999` |
| Fixture SQL | `performance/sql/gap-analysis-smoke-fixture.sql` |
| Check SQL | `performance/sql/gap-analysis-smoke-check.sql` |
| Smoke script | `performance/analytics/gap-analysis-api-smoke.sh` |
| Output dir | `docs/metrics/gap-analysis-smoke` |

## DB Console 사전 확인

먼저 fixture를 실행한다.

```sql
-- performance/sql/gap-analysis-smoke-fixture.sql 전체 실행
```

그 다음 아래 check SQL을 실행한다.

```sql
-- performance/sql/gap-analysis-smoke-check.sql 전체 실행
```

기대 fixture 결과:

| Metric | 기대값 |
| --- | --- |
| `user_project_id` | not null |
| `analysis_id` | not null |
| `user_project_skill_count` | `8` |
| `user_project_skills` | `AWS, Docker, Git, Java, MySQL, Redis, Spring Boot, Spring Framework` |

실제 fixture 결과:

| user_project_id | analysis_id | user_project_skill_count | user_project_skills |
| ---: | ---: | ---: | --- |
| TODO | TODO | TODO | TODO |

기대 index 결과:

| Metric | 기대값 |
| --- | --- |
| JUMPIT indexed open jobs | `> 0` |
| WANTED indexed open jobs | `> 0` |
| target role indexed open jobs | `> 0` |

실제 index 결과:

| source | open_job_count | indexed_open_job_count | required_indexed_open_job_count | preferred_indexed_open_job_count | indexed_skill_count |
| --- | ---: | ---: | ---: | ---: | ---: |
| JUMPIT | TODO | TODO | TODO | TODO | TODO |
| WANTED | TODO | TODO | TODO | TODO | TODO |

## API Smoke 실행 명령

```bash
BASE_URL=http://localhost:8080 \
USER_PROJECT_ID=<user_project_id> \
LIMIT=10 \
TARGET_ROLES=BACKEND,FULLSTACK,SOFTWARE_ENGINEER,DEVOPS \
EXPECT_MISSING_PROJECT_CHECK=true \
MISSING_PROJECT_ID=999999999 \
OUTPUT_DIR=docs/metrics/gap-analysis-smoke \
bash performance/analytics/gap-analysis-api-smoke.sh
```

기대 결과:

```text
Gap analysis API smoke completed.
### GET /gap-analysis/projects/999999999 should return USER_PROJECT_NOT_FOUND
Saved response: docs/metrics/gap-analysis-smoke/gap-analysis-api-response.json
Saved match summary: docs/metrics/gap-analysis-smoke/gap-analysis-match-summary.tsv
```

## API Smoke 검증 항목

smoke script는 다음 조건 중 하나라도 깨지면 실패한다.

| 검증 항목 | 실패 조건 |
| --- | --- |
| API success | `.success != true` |
| 비어 있지 않은 결과 | `.data.jobMatches`가 비어 있음 |
| limit contract | `.data.jobMatches.length`가 요청한 `LIMIT`을 초과함 |
| detail fields | skill detail field가 없거나 array가 아님 |
| target role filter | 응답에 `TARGET_ROLES` 밖의 role이 포함됨 |
| meaningful gap detail | 모든 skill detail list가 비어 있음 |
| match rate nullability | required/preferred match rate nullability가 skill bucket count와 맞지 않음 |
| missing project guard | missing project 요청이 `404` / `USER_PROJECT_NOT_FOUND`를 반환하지 않음 |

## 결과 요약

| Metric | 실제값 |
| --- | ---: |
| job match count | TODO |
| job match count <= limit | TODO |
| top match score | TODO |
| top required match rate | TODO |
| top preferred match rate | TODO |
| response contains target role only | TODO |
| response contains matched/missing skill details | TODO |
| match rate nullability is valid | TODO |
| missing project status | TODO |
| missing project error code | TODO |

## Top Match Sample

`gap-analysis-match-summary.tsv`의 상위 행을 기록한다.

| job_id | title | role | required_match_rate | preferred_match_rate | match_score | matched_required_skills | missing_required_skills | matched_preferred_skills | missing_preferred_skills |
| ---: | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- |
| TODO | TODO | TODO | TODO | TODO | TODO | TODO | TODO | TODO | TODO |

## 판정 기준

PASS 기준:

- fixture project가 8개 project skill을 가진다.
- `job_skill_index`에 real JUMPIT/WANTED open job이 색인되어 있다.
- Gap Analysis API가 비어 있지 않은 match 결과를 반환한다.
- Gap Analysis API가 요청한 `LIMIT` 이하의 결과만 반환한다.
- missing project 요청이 `404`와 `USER_PROJECT_NOT_FOUND`를 반환한다.
- 반환된 match가 요청한 target role 안에 있다.
- 응답에 matched/missing required/preferred skill detail이 포함된다.
- match rate는 해당 required/preferred skill bucket이 비어 있을 때만 `null`이다.

## 남은 리스크

| 리스크 | 현재 처리 | 후속 확인 |
| --- | --- | --- |
| match score weight가 heuristic이다 | smoke는 ranking 품질 최종판이 아니라 정렬과 설명 가능성을 검증한다 | labeled fixture 기준으로 score weight 튜닝 |
| project skill snapshot이 fixture 기반이다 | smoke는 deterministic static skill list를 사용한다 | 실제 GitHub analysis sample로 교체 |
| missing project smoke가 synthetic high id를 사용한다 | not-found guard는 검증하지만, 다른 실제 사용자 소유 project 접근은 검증하지 않는다 | multi-user smoke data 도입 시 cross-user ownership fixture 추가 |
| required/preferred 추출은 JD section parsing 품질에 의존한다 | 현재 index는 section을 분리하지만 source text 품질에 따라 달라질 수 있다 | required/preferred 분포를 계속 측정 |

## 결론

TODO: PASS or FAIL.
