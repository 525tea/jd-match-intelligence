# Frontend API Integration Smoke Report

작성일: 2026-06-19

## 목적

프론트엔드가 Gateway 기준 API 계약에 맞춰 실제 backend 데이터를 렌더링하는지 확인했다.
검증 범위는 공개 공고/트렌드 조회, 인증 경계, 프로젝트 분석 기반 화면, JD 매칭, 갭 분석, 추천 공고 조회다.

## 실행 환경

- Frontend URL: `http://127.0.0.1:5173`
- API proxy path: `/api`
- Gateway URL: `http://127.0.0.1:8081`
- Backend auth: HttpOnly cookie + token exchange response
- 검색 키워드: `backend`
- 기준 월: `2026-06-01`

## 검증 결과

| 항목 | 결과 |
| --- | --- |
| Frontend document load | 성공 |
| Vite proxy public API routing | 성공 |
| Job detail description section rendering | 성공 |
| Trend API routing | 성공 |
| Unauthenticated protected API boundary | 성공 |
| Authenticated user session check | 성공 |
| Project skill inventory rendering | 성공 |
| JD match rendering | 성공 |
| Gap analysis rendering | 성공 |
| Recommendation rendering | 성공 |

## Smoke Summary

```text
frontend_html_ok=true
jobs_search_status=200
job_detail_status=200
job_detail_description_section_count=1
trend_skills_status=200
auth_me_without_token_status=401
auth_me_with_token_status=200
saved_jobs_with_token_status=200
applications_with_token_status=200
project_skills_with_token_status=200
project_job_matches_with_token_status=200
gap_analysis_with_token_status=200
recommendations_with_token_status=200
project_skill_count=6
project_job_match_count=1
gap_job_match_count=1
recommendation_count=1
```

## 주요 확인 사항

### Public API

비로그인 상태에서도 공고 검색, 공고 상세, 트렌드 조회가 Gateway proxy를 통해 정상 동작했다.
공고 상세는 description section을 렌더링하며, 줄바꿈과 섹션 제목을 프론트에서 읽을 수 있는 형태로 표시한다.

### Auth Boundary

보호 API는 토큰 없이 호출하면 `401 COMMON_UNAUTHORIZED`를 반환한다.
로그인 후에는 `/auth/me`로 세션을 확인하고, project id를 복구해 프로젝트 분석 화면에 사용한다.

### Project 기반 화면

프로젝트 스킬 인벤토리, JD 매칭, 갭 분석, 추천 공고가 같은 project context를 기준으로 조회된다.
프론트는 smoke/fixture 같은 내부 evidence 문자열을 사용자 화면에 직접 노출하지 않고, 사용자용 설명 문구로 치환한다.

### 공고 상세

지원 버튼은 source와 external id를 기준으로 원본 공고 URL을 구성한다.
마감일이 없는 Wanted 공고는 `상시`로 단정하지 않고 `마감 정보 없음`으로 표시한다.

## 리스크

- 현재 데모 세션은 HttpOnly cookie 기반이지만, access token response는 메모리 힌트로만 사용한다. 새로고침 후에는 `/auth/me`로 세션을 복구한다.
- 프로젝트 생성/재분석 API는 별도 backend 작업 범위로 남아 있어, 프론트에서는 분석된 project id가 있는 경우의 조회 흐름을 우선 검증했다.
- Wanted 원본에서 deadline이 `null`인 공고는 정확한 마감일을 표시할 수 없으므로, source 재수집 또는 raw replay backfill 작업에서 추가 보강한다.
