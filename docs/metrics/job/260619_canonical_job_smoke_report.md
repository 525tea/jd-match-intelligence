# Canonical Job Apply URL Smoke Report

작성일: 2026-06-19

## 목적

source가 다른 동일 공고 후보를 `canonicalFingerprint` 기준으로 묶고, 사용자에게 보여줄 대표 지원 URL을 안정적으로 선택하는지 확인했다.

검증 범위는 다음과 같다.

- `jobs.canonical_fingerprint` 기반 중복 후보 조회
- 공고 목록과 상세 응답의 `canonicalFingerprint`, `applyUrl` 노출
- canonical group API 공개 접근
- 회사 원문 URL이 있는 경우 대표 지원 URL 우선 선택
- 프론트 상세 화면에서 사용할 수 있는 대표 지원 경로 계약 확인

## 실행 환경

| 항목 | 값 |
| --- | --- |
| 측정일 | `2026-06-19` |
| Gateway Base URL | `http://localhost:8081/api` |
| 실행 위치 | 로컬 Docker Compose backend/gateway |
| fixture source | `CANONICAL_SMOKE` |
| canonical fingerprint | `canonical-smoke\|backend-engineer\|seoul` |

## 검증 데이터

테스트 fixture는 같은 회사/직무/지역을 가진 공고 2건으로 구성했다.

| 항목 | 값 |
| --- | ---: |
| canonical smoke job count | 2 |
| canonical group count | 1 |
| company original URL count | 1 |
| open job count | 2 |

## 실행 명령

DB fixture:

```sql
-- performance/sql/canonical-job-smoke-fixture.sql
```

DB 검증:

```sql
-- performance/sql/canonical-job-smoke-check.sql
```

API smoke:

```bash
BASE_URL=http://localhost:8081/api \
bash performance/job/canonical-job-api-smoke.sh
```

## Smoke Summary

```text
jobs_status=200
selected_job_id=1502
fixture_job_count=2

job_detail_status=200
detail_canonical_fingerprint=canonical-smoke|backend-engineer|seoul
detail_apply_url=https://www.wanted.co.kr/wd/wanted-1001

canonical_group_status=200
group_fingerprint=canonical-smoke|backend-engineer|seoul
representative_job_id=1503
representative_apply_url=https://company.example.com/jobs/backend-engineer
duplicate_count=1
group_job_count=2
representative_count=1
```

## 검증 결과

| 항목 | 결과 |
| --- | --- |
| `GET /jobs` fixture 조회 | PASS |
| `GET /jobs/{jobId}` 상세 `canonicalFingerprint` 노출 | PASS |
| `GET /jobs/{jobId}` 상세 `applyUrl` 노출 | PASS |
| `GET /jobs/{jobId}/canonical-group` 공개 접근 | PASS |
| canonical group job count | 2 |
| duplicate count | 1 |
| representative count | 1 |
| 대표 지원 URL 선택 | 회사 원문 URL 우선 선택 |

## 주요 확인 사항

### 공고 상세 apply URL

선택된 공고 상세는 source detail URL을 `applyUrl`로 노출했다.
예시 fixture에서는 WANTED 공고 상세 URL이 반환됐다.

### Canonical Group

같은 `canonicalFingerprint`를 가진 2건이 하나의 group으로 묶였다.
group API는 토큰 없이 접근 가능한 공개 공고 조회 API로 동작한다.

### 대표 지원 URL 정책

같은 공고 후보 중 회사 원문 URL이 있는 공고를 대표 공고로 선택했다.
따라서 사용자가 `지원하기`를 누르면 플랫폼 중복 후보보다 실제 회사 원문 지원 페이지로 이동할 수 있다.

## 회귀 방지

이번 smoke 중 `/jobs/{jobId}/canonical-group`이 SecurityConfig 공개 경로에서 빠져 `401`이 발생했다.
수정 후 다음 회귀 테스트를 추가했다.

- 인증 토큰 없이 canonical group API 호출 가능
- OpenAPI docs에 `/jobs/{jobId}/canonical-group` path 노출

## 결론

canonical group + 대표 지원 URL 정책은 정상 동작한다.

- 같은 공고 후보를 `canonicalFingerprint`로 묶을 수 있다.
- source별 지원 URL은 보존된다.
- 대표 URL은 회사 원문 URL을 우선한다.
- 프론트 상세 화면은 backend의 `applyUrl`과 canonical group 응답을 기준으로 지원 경로를 렌더링할 수 있다.
