# Job Recommendation API Smoke Report

## 목적

프로젝트 분석 결과와 실제 공고 인덱스를 기반으로 추천 API가 end-to-end로 동작하는지 확인한다.

이번 smoke는 다음 항목을 검증한다.

- 사용자 프로젝트 소유권 검증
- 최신 project analysis 기반 skill snapshot 조회
- `job_skill_index` 기반 추천 후보 산출
- 추천 score breakdown 반환
- 사용자 행동 상태 기반 ignored 공고 제외
- 추천 결과 `totalScore` 내림차순 정렬
- missing project 404 guard

## 측정 기준

| 항목 | 값 |
| --- | --- |
| 측정일 | 2026-06-15 |
| API | `GET /recommendations/jobs` |
| API base URL | `http://localhost:8080` |
| Project source | local smoke project |
| Target roles | `BACKEND,FULLSTACK` |
| Limit | `10` |
| Missing project check | enabled |
| Smoke script | `performance/recommendation/job-recommendation-api-smoke.sh` |
| Raw response | local only |

## 실행 명령

```bash
USER_PROJECT_ID=<user_project_id> \
ACCESS_TOKEN='<JobFlow JWT>' \
TARGET_ROLES=BACKEND,FULLSTACK \
LIMIT=10 \
bash performance/recommendation/job-recommendation-api-smoke.sh
```

주의:

- `ACCESS_TOKEN`은 JobFlow 로그인 후 발급되는 JWT다.
- GitHub provider access token, GitHub Client Secret, GitHub Actions token, encryption key를 넣지 않는다.
- 실제 token 값은 문서에 기록하지 않는다.
- 사용자 이메일, 실제 계정명, 개인 식별자는 문서에 기록하지 않는다.

## Smoke 결과 요약

| Metric | Actual |
| --- | ---: |
| API success | true |
| recommendation count | 10 |
| top job id | 18 |
| top total score | 82.67 |
| top skill match score | 40.00 |
| top freshness score | 10.67 |
| top behavior score | 30.00 |
| top popularity score | 2.00 |
| ignored status count | 0 |

## 검증 항목

| Check | Result |
| --- | --- |
| response `success=true` | PASS |
| recommendation count >= 1 | PASS |
| required response fields present | PASS |
| `IGNORED` jobs excluded | PASS |
| sorted by `totalScore DESC` | PASS |
| missing project returns `USER_PROJECT_NOT_FOUND` | PASS |

## Score Breakdown

상위 추천 결과는 다음 점수 필드를 포함했다.

| Factor | Observed |
| --- | ---: |
| skill match score | 40.00 |
| freshness score | 10.67 |
| behavior score | 30.00 |
| popularity score | 2.00 |
| total score | 82.67 |

응답에는 다음 detail field가 포함된다.

| Field group | Result |
| --- | --- |
| job metadata | jobId, source, title, companyName, role, careerLevel 반환 |
| status metadata | status, userJobStatus 반환 |
| score breakdown | total + factor별 점수 반환 |
| required skills | matched / missing 분리 |
| preferred skills | matched / missing 분리 |

## 해석

Recommendation API는 프로젝트 분석 snapshot과 공고 인덱스를 연결해 추천 결과를 반환한다.

이번 smoke에서 확인한 핵심은 다음과 같다.

- 추천 결과가 실제 API response contract로 반환된다.
- 사용자에게 무시된 공고는 추천 결과에서 제외된다.
- 추천 결과는 `totalScore` 기준 내림차순으로 정렬된다.
- score는 단일 숫자가 아니라 skill match, freshness, behavior, popularity 축으로 분해된다.

## 남은 리스크

현재 smoke dataset에는 local seed / smoke fixture 성격의 공고가 함께 포함될 수 있다.

따라서 이 리포트는 API 연결성, response contract, ranking guard 검증에는 유효하지만, 실제 추천 품질 판단은 real-source-only dataset 기준으로 별도 측정하는 것이 좋다.

후속 추천 품질 검증에서는 다음 조건을 분리해서 확인한다.

- real source only
- fixture source excluded
- target role별 추천 품질
- 사용자 행동 데이터가 충분히 쌓인 상태의 ranking 변화
- top-k 결과의 실사용 relevance

## 결론

PASS.

`GET /recommendations/jobs`는 local smoke 기준으로 project inventory, job skill index, recommendation score, user behavior guard를 end-to-end로 연결했다.
