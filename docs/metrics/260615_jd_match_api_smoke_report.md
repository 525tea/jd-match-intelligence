# JD Match API Smoke Report

## 목적

프로젝트 분석 결과와 실제 공고 인덱스를 연결해 JD 매칭 API가 end-to-end로 동작하는지 확인한다.

이번 smoke는 다음 항목을 검증한다.

- 사용자 프로젝트 소유권 검증
- 최신 project analysis 기반 skill / experience tag snapshot 조회
- `job_skill_index` 기반 required / preferred skill 매칭
- 공고 experience tag와 프로젝트 experience tag 비교
- 5-factor JD match score 반환
- missing project 404 guard

## 측정 기준

| 항목 | 값 |
| --- | --- |
| 측정일 | 2026-06-15 |
| API | `GET /projects/{projectId}/job-matches` |
| API base URL | `http://localhost:8080` |
| Project source | local smoke project |
| Target roles | `BACKEND,FULLSTACK` |
| Target career level | `MID` |
| Limit | `10` |
| Missing project check | enabled |
| Check SQL | `performance/sql/jd-match-smoke-check.sql` |
| Smoke script | `performance/matching/jd-match-api-smoke.sh` |
| Raw response | local only |

## DB Console 사전 확인

`performance/sql/jd-match-smoke-check.sql`로 smoke 대상 project analysis와 real source job skill index를 확인했다.

프로젝트 분석 snapshot:

| user_project_id | user_id | analysis_id | analysis_version | project_skill_count | project_experience_tag_count | project_skills | project_experience_tags |
| ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| 2 | 4 | 2 | 1 | 6 | 3 | AWS, Docker, Git, Java, MySQL, Spring Boot | CI_CD, CLOUD_INFRA, TESTING |
| 1 | 2 | 1 | 1 | 8 | 0 | AWS, Docker, Git, Java, MySQL, Redis, Spring Boot, Spring Framework | |

소유권 확인:

| project | result |
| --- | --- |
| `user_project_id=1` | 로그인 사용자 소유가 아니므로 `USER_PROJECT_NOT_FOUND` |
| `user_project_id=2` | 로그인 사용자 소유 project로 smoke 대상 |

## 실행 명령

```bash
USER_PROJECT_ID=2 \
ACCESS_TOKEN='<JobFlow JWT>' \
TARGET_ROLES=BACKEND,FULLSTACK \
TARGET_CAREER_LEVEL=MID \
LIMIT=10 \
bash performance/matching/jd-match-api-smoke.sh
```

주의:

- `ACCESS_TOKEN`은 JobFlow 로그인 후 발급되는 JWT다.
- GitHub provider access token, GitHub Client Secret, encryption key를 넣지 않는다.
- 실제 token 값은 문서에 기록하지 않는다.

## Smoke 결과 요약

| Metric | Actual |
| --- | ---: |
| API success | true |
| match count | 10 |
| top total score | 79.95 |
| top required skill score | 45.00 |
| top preferred skill score | 20.00 |
| top experience tag score | 0.00 |
| top career level score | 10.00 |
| top confidence score | 4.95 |
| missing project status | 404 |
| missing project error code | `USER_PROJECT_NOT_FOUND` |

## Top Match Breakdown

상위 결과는 다음 5-factor 점수 필드를 모두 포함했다.

| Factor | Observed |
| --- | ---: |
| required skill score | 45.00 |
| preferred skill score | 20.00 |
| experience tag score | 0.00 |
| career level score | 10.00 |
| confidence score | 4.95 |
| total score | 79.95 |

응답에는 다음 detail field가 포함된다.

| Field group | Result |
| --- | --- |
| required skills | matched / missing 분리 |
| preferred skills | matched / missing 분리 |
| experience tags | matched / missing 분리 |
| score breakdown | total + factor별 점수 반환 |
| project metadata | userProjectId, analysisId, analysisVersion, analyzedAt 반환 |
| job metadata | jobId, title, companyName, role, careerLevel 반환 |

## Missing Project Guard

없는 project id로 요청하면 다음 응답을 반환했다.

```json
{
  "success": false,
  "error": {
    "code": "USER_PROJECT_NOT_FOUND",
    "message": "사용자 프로젝트를 찾을 수 없습니다."
  }
}
```

이는 다음 두 경우를 같은 방식으로 보호한다.

- 존재하지 않는 project
- 로그인 사용자가 소유하지 않은 project

## 해석

JD Match API는 프로젝트 분석 snapshot과 공고 인덱스를 연결해 explainable matching response를 반환한다.

단순 score만 반환하지 않고, 다음 정보를 함께 제공하므로 프론트에서 JD 매칭 근거를 설명할 수 있다.

- 어떤 필수 스킬이 맞았는지
- 어떤 필수 스킬이 부족한지
- 어떤 우대 스킬이 맞았는지
- 어떤 우대 스킬이 부족한지
- 어떤 경험 태그가 맞았는지
- 어떤 경험 태그가 부족한지
- 총점이 어떤 factor에서 나왔는지

## 남은 리스크

현재 smoke dataset에는 local seed / smoke fixture 성격의 공고가 함께 포함될 수 있다.

따라서 이 리포트는 API 연결성과 score contract 검증에는 유효하지만, 실제 사용자 추천 품질 판단은 real-source-only dataset 기준으로 별도 측정하는 것이 좋다.

후속 추천 품질 검증에서는 다음 조건을 분리해서 확인한다.

- real source only
- fixture source excluded
- target role별 결과 품질
- target career level별 결과 품질
- top-k 결과의 실사용 relevance

## 결론

PASS.

`GET /projects/{projectId}/job-matches`는 local smoke 기준으로 project inventory, job skill index, experience tag, 5-factor score, ownership guard를 end-to-end로 연결했다.
