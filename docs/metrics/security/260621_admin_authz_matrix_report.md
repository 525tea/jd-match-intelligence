# Admin Authorization Matrix Smoke Report

작성일: 2026-06-21

## 목적

ADMIN 권한이 필요한 기준 데이터 관리 API가 public, anonymous, USER, ADMIN 요청을 올바르게 구분하는지 확인한다.

이번 검증은 다음 인증/인가 경계를 확인한다.

- 공개 API는 토큰 없이 접근 가능해야 한다.
- 사용자 보호 API는 토큰 없이 `401 COMMON_UNAUTHORIZED`를 반환해야 한다.
- ADMIN API는 토큰 없이 `401 COMMON_UNAUTHORIZED`를 반환해야 한다.
- ADMIN API는 USER 토큰으로 호출하면 `403 COMMON_FORBIDDEN`을 반환해야 한다.
- ADMIN API는 ADMIN 토큰으로 호출하면 정상 처리되어야 한다.
- 인증/인가 실패 응답에는 `timestamp`, `path` metadata가 포함되어야 한다.

민감정보는 기록하지 않는다.

- 실제 JWT token은 기록하지 않는다.
- 실제 관리자 비밀번호는 기록하지 않는다.
- 실제 OAuth authorization code는 기록하지 않는다.

## 실행 환경

| 항목 | 값 |
| --- | --- |
| 실행일 | `2026-06-21` |
| Gateway Base URL | `http://localhost:8081/api` |
| 실행 위치 | 로컬 Docker Compose backend/gateway |
| 인증 방식 | JobFlow JWT |
| USER token | provided |
| ADMIN token | provided |

## 사전 조건

ADMIN 계정은 backend 시작 시점에 bootstrap 환경변수로 provisioning한다.

```dotenv
ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_BOOTSTRAP_EMAIL=ADMIN_EMAIL
ADMIN_BOOTSTRAP_PASSWORD=ADMIN_PASSWORD
ADMIN_BOOTSTRAP_NAME=Admin
```

Docker Compose backend service는 위 환경변수를 backend container로 전달해야 한다.

## 검증 스크립트

```text
performance/security/admin-authz-matrix-smoke.sh
```

## 실행 명령

```bash
BASE_URL=http://localhost:8081/api \
ACCESS_TOKEN='USER_JWT_TOKEN' \
ADMIN_ACCESS_TOKEN='ADMIN_JWT_TOKEN' \
bash performance/security/admin-authz-matrix-smoke.sh
```

## Smoke 결과

| 구간 | API | 기대값 | 결과 |
| --- | --- | ---: | ---: |
| Public API without token | `GET /skills` | 200 | 200 |
| Public API without token | `GET /jobs/search` | 200 | 200 |
| Public API without token | `GET /trends/skills` | 200 | 200 |
| Protected USER API without token | `GET /auth/me` | 401 | 401 |
| Admin API without token | `POST /skills` | 401 | 401 |
| Admin API with USER token | `POST /skills` | 403 | 403 |
| Admin API with ADMIN token | `POST /skills` | 201 | 201 |

## Error Response 확인

### 인증 없음

`GET /auth/me` without token:

```json
{
  "success": false,
  "error": {
    "code": "COMMON_UNAUTHORIZED",
    "message": "인증이 필요합니다."
  },
  "timestamp": "present",
  "path": "/auth/me"
}
```

`POST /skills` without token:

```json
{
  "success": false,
  "error": {
    "code": "COMMON_UNAUTHORIZED",
    "message": "인증이 필요합니다."
  },
  "timestamp": "present",
  "path": "/skills"
}
```

### 권한 부족

`POST /skills` with USER token:

```json
{
  "success": false,
  "error": {
    "code": "COMMON_FORBIDDEN",
    "message": "접근 권한이 없습니다."
  },
  "timestamp": "present",
  "path": "/skills"
}
```

### ADMIN 성공

`POST /skills` with ADMIN token:

```json
{
  "success": true,
  "data": {
    "name": "Smoke Admin Skill <SMOKE_RUN_ID>",
    "normalizedName": "smoke-admin-skill-<SMOKE_RUN_ID>",
    "category": "TOOL"
  }
}
```

## Smoke Summary

```text
public_skills_status=200
public_jobs_search_status=200
public_trend_skills_status=200
auth_me_without_token_status=401
admin_without_token_status=401
admin_with_user_status=403
admin_with_admin_status=201

API authorization matrix smoke completed.
```

## 발견한 문제와 수정

### Docker Compose admin bootstrap env 누락

문제:

- backend application에는 `ADMIN_BOOTSTRAP_*` 설정이 있었지만 Docker Compose backend service가 해당 환경변수를 전달하지 않았다.
- 따라서 `.env`에 admin bootstrap 값을 설정해도 container 안에서는 값이 비어 admin 계정이 생성되지 않았다.

수정:

- `docker-compose.yml`의 backend service에 `ADMIN_BOOTSTRAP_ENABLED`, `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD`, `ADMIN_BOOTSTRAP_NAME` 전달을 추가했다.

### Smoke script 중복 POST 호출

문제:

- 기존 smoke script는 status 확인과 body 확인을 위해 `POST /skills`를 두 번 호출했다.
- 첫 번째 요청이 `201 Created`로 성공한 뒤, 두 번째 요청이 같은 `normalizedName`으로 다시 생성을 시도해 `409 Conflict`가 발생할 수 있었다.

수정:

- `curl --output <tempfile> --write-out "%{http_code}"` 방식으로 단일 요청에서 status와 body를 함께 캡처하도록 수정했다.

## 판단

현재 ADMIN 권한 경계는 의도한 대로 동작한다.

- anonymous 요청은 `401`
- USER token 요청은 `403`
- ADMIN token 요청은 `201`
- 실패 응답은 공통 error envelope과 metadata를 포함한다.

이 결과로 기준 데이터 관리 API의 최소 운영 권한 경계를 smoke 수준에서 재현 가능하게 검증할 수 있다.
