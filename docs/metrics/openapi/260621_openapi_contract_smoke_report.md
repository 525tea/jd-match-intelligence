# OpenAPI Contract Smoke Report

작성일: 2026-06-21

## 목적

Application/UserJob 상태 API 보강 후 Gateway 기준 OpenAPI 계약이 실제 문서에 반영됐는지 확인한다.

이번 검증은 다음 범위를 확인한다.

- OpenAPI 문서 접근
- Swagger UI 접근
- JWT bearerAuth scheme 노출
- 핵심 API path 노출
- UserJob 저장/무시 취소 API 계약 노출
  - `DELETE /user/jobs/{jobId}/save`
  - `DELETE /user/jobs/{jobId}/ignore`
- Application 상태 변경 이력 API 계약 노출
  - `GET /applications/{applicationId}/status-histories`

민감정보는 기록하지 않는다.

- 실제 JWT token은 기록하지 않는다.
- OAuth authorization code는 기록하지 않는다.
- 실제 사용자 이메일은 기록하지 않는다.

## 실행 환경

| 항목 | 값 |
| --- | --- |
| 실행일 | `2026-06-21` |
| Gateway Base URL | `http://localhost:8081/api` |
| OpenAPI docs | `/v3/api-docs` |
| Swagger UI | `/swagger-ui/index.html` |
| 실행 위치 | 로컬 Docker Compose backend/gateway |

## 검증 스크립트

```text
performance/openapi/openapi-contract-smoke.sh
```

## 실행 명령

```bash
BASE_URL=http://localhost:8081/api \
bash performance/openapi/openapi-contract-smoke.sh
```

## Smoke 결과

### OpenAPI 문서

| 항목 | 결과 |
| --- | --- |
| title | `JobFlow API` |
| openapi | `3.1.0` |
| path_count | `34` |
| has_bearer_auth | `true` |
| operation_level_security_count | `0` |

### 핵심 계약 확인

| 영역 | 항목 | 결과 |
| --- | --- | --- |
| Auth | `/auth/login` | `true` |
| Auth | `/auth/me` | `true` |
| Jobs | `/jobs/search` | `true` |
| Jobs | `/jobs/{jobId}` | `true` |
| UserJob | `POST /user/jobs/{jobId}/view` | `true` |
| UserJob | `POST /user/jobs/{jobId}/save` | `true` |
| UserJob | `DELETE /user/jobs/{jobId}/save` | `true` |
| UserJob | `POST /user/jobs/{jobId}/ignore` | `true` |
| UserJob | `DELETE /user/jobs/{jobId}/ignore` | `true` |
| Applications | `/applications` | `true` |
| Applications | `GET /applications/{applicationId}/status-histories` | `true` |
| Project Analytics | `/projects/{userProjectId}/skills` | `true` |
| Project Analytics | `/projects/{userProjectId}/experience-tags` | `true` |
| Project Analytics | `/projects/{userProjectId}/job-matches` | `true` |
| Gap Analysis | `/gap-analysis/projects/{userProjectId}` | `true` |
| Recommendations | `/recommendations/jobs` | `true` |
| Trends | `/trends/skills` | `true` |

## Smoke Summary

```text
### OpenAPI Contract Summary
title=JobFlow API
openapi=3.1.0
path_count=34
has_bearer_auth=true
operation_level_security_count=0
has_auth_login=true
has_auth_me=true
has_jobs_search=true
has_job_detail=true
has_user_job_view=true
has_user_job_save=true
has_user_job_unsave=true
has_user_job_ignore=true
has_user_job_unignore=true
has_applications=true
has_application_detail=true
has_application_status_update=true
has_application_status_histories=true
has_project_skills=true
has_project_experience_tags=true
has_jd_matches=true
has_gap_analysis=true
has_recommendations=true
has_trend_skills=true

OpenAPI contract smoke completed.
```

## 판단

OpenAPI 계약 스모크는 Gateway 기준으로 통과했다.

- UserJob 저장/무시 처리 API가 기존 계약에 유지됐다.
- UserJob 저장 취소/무시 취소 API가 OpenAPI 문서에 반영됐다.
- Application 상태 변경 이력 조회 API가 OpenAPI 문서에 반영됐다.
- Application/UserJob 상태 API 보강 후 프론트와 스모크 스크립트가 참조할 공개 API 계약이 최신 상태로 갱신됐다.
