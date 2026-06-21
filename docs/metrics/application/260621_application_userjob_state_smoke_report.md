# Application/UserJob State Smoke Report

작성일: 2026-06-21

## 목적

지원 상태와 사용자 공고 행동 상태가 Gateway 기준 실제 API에서 의도대로 동작하는지 확인한다.

이번 검증은 다음 흐름을 확인한다.

- 인증 사용자가 `GET /jobs/{jobId}`로 공고 상세를 조회하면 UserJob 상태가 `VIEWED`로 자동 기록된다.
- 사용자는 공고를 저장, 저장 취소, 무시, 무시 취소할 수 있다.
- 저장/무시 목록 API는 `page`, `size` 요청 파라미터를 받아 목록 크기를 제한한다.
- 지원 상태는 허용된 방향으로만 전이된다.
- 역방향 지원 상태 전이는 `409 APPLICATION_STATUS_CONFLICT`로 거부된다.
- 모든 검증은 Gateway `http://localhost:8081/api`를 기준으로 수행한다.

민감정보는 기록하지 않는다.

- 실제 JWT token은 기록하지 않는다.
- 실제 사용자 비밀번호는 기록하지 않는다.
- OAuth authorization code는 기록하지 않는다.

## 실행 환경

| 항목 | 값 |
| --- | --- |
| 실행일 | `2026-06-21` |
| Gateway Base URL | `http://localhost:8081/api` |
| 실행 위치 | 로컬 Docker Compose backend/gateway |
| 인증 방식 | JobFlow JWT |
| USER token | provided |
| 검색어 | `backend` |
| 검색 limit | `5` |

## 검증 스크립트

```text
performance/application/application-userjob-state-smoke.sh
```

## 실행 명령

Postman 등에서 발급한 USER JWT를 사용할 때:

```bash
BASE_URL=http://localhost:8081/api \
ACCESS_TOKEN='USER_JWT_TOKEN' \
bash performance/application/application-userjob-state-smoke.sh
```

일반 로그인 API로 USER JWT를 바로 받아 실행할 때:

```bash
ACCESS_TOKEN="$(
  curl -s -X POST 'http://localhost:8081/api/auth/login' \
    -H 'Content-Type: application/json' \
    -d '{"email":"frontend-demo@example.com","password":"password123"}' \
  | jq -r '.data.accessToken'
)" \
BASE_URL=http://localhost:8081/api \
bash performance/application/application-userjob-state-smoke.sh
```

## Smoke 결과

### 인증 및 공고 선택

| 구간 | API | 기대값 | 결과 |
| --- | --- | ---: | ---: |
| 인증 확인 | `GET /auth/me` | 200 | 200 |
| 공고 검색 | `GET /jobs/search?keyword=backend&limit=5` | 200 | 200 |

선택된 공고:

| 항목 | 값 |
| --- | --- |
| job id | `13` |
| title | `백엔드 개발자` |

### UserJob 상태 흐름

| 구간 | API | 기대값 | 결과 |
| --- | --- | ---: | ---: |
| 공고 상세 조회 | `GET /jobs/{jobId}` with token | 200 | 200 |
| 상세 조회 후 UserJob 확인 | `GET /user/jobs/{jobId}` | 200 | 200 |
| 저장 처리 | `POST /user/jobs/{jobId}/save` | 200 | 200 |
| 저장 목록 page 조회 | `GET /user/jobs/saved?page=0&size=1` | 200 | 200 |
| 저장 취소 | `DELETE /user/jobs/{jobId}/save` | 200 | 200 |
| 무시 처리 | `POST /user/jobs/{jobId}/ignore` | 200 | 200 |
| 무시 목록 page 조회 | `GET /user/jobs/ignored?page=0&size=1` | 200 | 200 |
| 무시 취소 | `DELETE /user/jobs/{jobId}/ignore` | 200 | 200 |

확인한 상태 전이:

```text
GET /jobs/{jobId} -> VIEWED -> SAVED -> VIEWED -> IGNORED -> VIEWED
```

목록 조회 확인:

```text
GET /user/jobs/saved?page=0&size=1
GET /user/jobs/ignored?page=0&size=1
```

두 API 모두 응답 배열 크기가 `size=1` 제한을 넘지 않는 것을 확인했다.

### Application 상태 전이

| 구간 | API | 기대값 | 결과 |
| --- | --- | ---: | ---: |
| 지원 생성 | `POST /applications` | 201 | 201 |
| 상태 변경 | `PATCH /applications/{applicationId}/status` to `INTERVIEW` | 200 | 200 |
| 역방향 전이 거부 | `PATCH /applications/{applicationId}/status` to `DOCUMENT_PASSED` | 409 | 409 |

확인한 상태 전이:

```text
APPLIED -> INTERVIEW
INTERVIEW -> DOCUMENT_PASSED rejected
```

역방향 전이는 `APPLICATION_STATUS_CONFLICT`로 거부된다.

## Smoke Summary

```text
user_id=75
selected_job_id=13
job_detail_status=200
user_job_auto_view_status=200
user_job_save_status=200
user_job_unsave_status=200
user_job_ignore_status=200
user_job_unignore_status=200
application_id=4
application_interview_status=200
application_invalid_transition_status=409

Application/UserJob state smoke completed.
```

## 발견한 문제와 수정

### Docker backend rebuild 누락

문제:

- `GET /user/jobs/saved?page=0&size=1` 호출 시 응답이 2건 이상 내려왔다.
- 원인은 새로 추가한 `page`, `size` 처리 코드가 반영된 backend image로 재빌드되지 않은 상태였다.

수정:

- backend/gateway를 새 코드 기준으로 재빌드 후 재실행했다.

```bash
docker compose up -d --build backend gateway
```

### JWT placeholder 사용

문제:

- `ACCESS_TOKEN='JWT_TOKEN'`처럼 placeholder 문자열을 그대로 넣으면 `/auth/me`가 `401`을 반환한다.

수정:

- 실제 USER JWT를 넣거나, 일반 로그인 API로 토큰을 즉시 발급받아 smoke를 실행했다.

## 판단

현재 Application/UserJob 상태 API는 Gateway 기준 smoke 검증을 통과했다.

- 인증 사용자의 공고 상세 조회가 UserJob `VIEWED` 상태를 자동 기록한다.
- UserJob 저장/무시 취소 API가 정상 동작한다.
- 저장/무시 목록 API가 `page`, `size` 요청 파라미터를 반영한다.
- Application 상태 전이는 허용된 방향으로만 진행된다.
- 잘못된 역방향 전이는 `409 APPLICATION_STATUS_CONFLICT`로 거부된다.

이 결과로 프론트의 공고 상세 조회, 저장/무시 토글 UX, 지원 상태 변경 UX를 실제 API 기반으로 연결할 수 있는 상태 계약이 확보됐다.
