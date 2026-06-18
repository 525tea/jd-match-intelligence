# Core API Acceptance / OpenAPI Contract Report

## 목적

Gateway 경유 API가 프론트엔드 연동 전에 필요한 최소 계약과 핵심 사용자 플로우를 만족하는지 확인한다.

이번 검증은 다음 범위를 확인한다.

- OpenAPI 문서와 Swagger UI 접근
- JWT bearerAuth scheme 노출
- 핵심 API path/method 노출
- 공개 API와 보호 API의 인증 경계
- 로그인 사용자 기준 공고 검색, 상세 조회, 저장, 지원 상태 변경 플로우
- 프로젝트 분석 기반 API 묶음
  - Project skill inventory
  - Project experience tag inventory
  - JD match
  - Gap analysis
  - Job recommendation

민감정보는 기록하지 않는다.

- 실제 JWT token은 기록하지 않는다.
- OAuth authorization code는 기록하지 않는다.
- 실제 사용자 이메일은 기록하지 않는다.
- 로컬 실행자의 개인 정보는 기록하지 않는다.

## 실행 환경

| 항목 | 값 |
| --- | --- |
| 측정일 | `2026-06-18` |
| Gateway Base URL | `http://localhost:8081/api` |
| 인증 방식 | JWT |
| 실행 위치 | 로컬 Docker Compose backend/gateway |
| OpenAPI docs | `/v3/api-docs` |
| Swagger UI | `/swagger-ui/index.html` |

## 검증 스크립트

| 스크립트 | 목적 |
| --- | --- |
| `performance/openapi/openapi-contract-smoke.sh` | OpenAPI 문서 계약과 핵심 path 확인 |
| `performance/openapi/api-auth-boundary-smoke.sh` | 공개/보호 API 인증 경계 확인 |
| `performance/openapi/core-api-acceptance-smoke.sh` | 공고 검색/저장/지원 상태 핵심 사용자 플로우 확인 |
| `performance/openapi/analytics-api-acceptance-smoke.sh` | 프로젝트 분석 기반 API 묶음 확인 |

## OpenAPI Contract Smoke

### 실행 명령

```bash
BASE_URL=http://localhost:8081/api \
bash performance/openapi/openapi-contract-smoke.sh
```

### 검증 항목

| 항목 | 결과 |
| --- | --- |
| OpenAPI title | `JobFlow API` |
| OpenAPI document 접근 | PASS |
| Swagger UI 접근 | PASS |
| bearerAuth type | `http` |
| bearerAuth scheme | `bearer` |
| bearerAuth format | `JWT` |
| 핵심 API path 노출 | PASS |

### 확인한 주요 path

| API 영역 | Path |
| --- | --- |
| Auth | `/auth/login`, `/auth/signup`, `/auth/me`, `/auth/oauth2/token` |
| Jobs | `/jobs`, `/jobs/search`, `/jobs/{jobId}` |
| User Jobs | `/user/jobs/{jobId}/view`, `/user/jobs/{jobId}/save`, `/user/jobs/{jobId}/ignore` |
| Applications | `/applications`, `/applications/{applicationId}`, `/applications/{applicationId}/status` |
| Project Analytics | `/projects/{userProjectId}/skills`, `/projects/{userProjectId}/experience-tags`, `/projects/{userProjectId}/job-matches` |
| Gap Analysis | `/gap-analysis/projects/{userProjectId}` |
| Recommendation | `/recommendations/jobs` |
| Trends | `/trends/skills`, `/trends/market` |

## Auth Boundary Smoke

### 실행 명령

```bash
BASE_URL=http://localhost:8081/api \
ACCESS_TOKEN='JWT_TOKEN' \
bash performance/openapi/api-auth-boundary-smoke.sh
```

### 결과

| 항목 | 결과 |
| --- | --- |
| `/jobs/search` without token | `200` |
| `/trends/skills` without token | `200` |
| Protected APIs without token | `401` |
| Protected error code | `COMMON_UNAUTHORIZED` |
| `/auth/me` with token | `200` |

### 해석

공개 API는 토큰 없이 접근 가능하고, 사용자 데이터가 필요한 보호 API는 토큰 없이 `401 COMMON_UNAUTHORIZED`를 반환한다.

Gateway가 `Authorization` header를 backend로 pass-through하는 것도 `/auth/me` token 검증으로 확인했다.

## Core API Acceptance Smoke

### 실행 명령

```bash
BASE_URL=http://localhost:8081/api \
ACCESS_TOKEN='JWT_TOKEN' \
bash performance/openapi/core-api-acceptance-smoke.sh
```

### 결과

| 항목 | 결과 |
| --- | --- |
| `/auth/me` | `200` |
| `/jobs/search` | `200` |
| `/jobs/{jobId}` | `200` |
| `/user/jobs/{jobId}/view` | `200` |
| `/user/jobs/{jobId}/save` | `200` |
| `/user/jobs/saved` | `200` |
| `/applications` create | `201` |
| `/applications` list | `200` |
| `/applications/{applicationId}` | `200` |
| `/applications/{applicationId}/status` | `200` |

### Smoke 값

| 항목 | 값 |
| --- | ---: |
| selected_job_id | 13 |
| application_id | 3 |
| application_status | `INTERVIEW` |
| saved_job_verified | true |
| application_flow_verified | true |

### 해석

프론트엔드가 사용할 핵심 사용자 플로우가 Gateway 경유로 동작한다.

검증한 흐름:

1. 인증 사용자 확인
2. 공고 검색
3. 공고 상세 조회
4. 공고 조회 기록
5. 공고 저장
6. 저장 목록 반영 확인
7. 지원 생성
8. 지원 목록 조회
9. 지원 상세 조회
10. 지원 상태 변경

## Analytics API Acceptance Smoke

### 실행 명령

```bash
BASE_URL=http://localhost:8081/api \
ACCESS_TOKEN='JWT_TOKEN' \
USER_PROJECT_ID=2 \
TARGET_ROLES=BACKEND,FULLSTACK \
LIMIT=5 \
bash performance/openapi/analytics-api-acceptance-smoke.sh
```

### 결과

| 항목 | 결과 |
| --- | --- |
| `/projects/{userProjectId}/skills` | `200` |
| `/projects/{userProjectId}/experience-tags` | `200` |
| `/projects/{userProjectId}/job-matches` | `200` |
| `/gap-analysis/projects/{userProjectId}` | `200` |
| `/recommendations/jobs` | `200` |
| Missing project check | `404 USER_PROJECT_NOT_FOUND` |

### Smoke 값

| 항목 | 값 |
| --- | ---: |
| project_skill_count | 6 |
| project_experience_tag_count | 3 |
| jd_match_count | 5 |
| top_jd_match_job_id | 1498 |
| top_jd_match_score | 94.95 |
| gap_user_skill_count | 6 |
| gap_match_count | 5 |
| recommendation_count | 5 |
| top_recommendation_job_id | 1498 |
| top_recommendation_score | 95.33 |

### 해석

프로젝트 분석 기반 API들이 같은 `userProjectId` 기준으로 정상 동작한다.

JD match와 recommendation의 top job이 동일하게 `1498`로 나와, 현재 fixture 기준에서는 매칭/추천 결과가 일관된 방향으로 정렬된다.

없는 프로젝트에 대해서는 `USER_PROJECT_NOT_FOUND`를 반환해 사용자 프로젝트 소유권 경계도 확인했다.

## Backend OpenAPI CI Test

### 실행 명령

```bash
./gradlew :backend:test --tests jobflow.global.config.OpenApiDocsIntegrationTest
```

### 검증 항목

| 항목 | 결과 |
| --- | --- |
| `/v3/api-docs` 공개 접근 | PASS |
| Swagger UI 공개 접근 | PASS |
| bearerAuth scheme | PASS |
| 핵심 API path 노출 | PASS |

## 결론

PASS.

Gateway 경유 핵심 API는 프론트엔드 연동 전 기준에서 다음 조건을 만족한다.

- OpenAPI 문서가 핵심 API 계약을 노출한다.
- Swagger UI가 접근 가능하다.
- JWT bearerAuth scheme이 문서화되어 있다.
- 공개 API와 보호 API의 인증 경계가 실제 HTTP 응답으로 검증됐다.
- 공고 검색, 저장, 지원 상태 변경까지 사용자 핵심 플로우가 동작한다.
- 프로젝트 분석 기반 API 묶음이 동일 project 기준으로 정상 응답한다.
