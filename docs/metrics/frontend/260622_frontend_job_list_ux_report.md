# Frontend Job List UX Smoke Report

작성일: 2026-06-22

## 목적

공고 탐색 화면의 기본 조회, 검색, 필터 적용 흐름이 백엔드 API 책임과 일치하는지 검증했다.
이번 검증은 프론트가 초기 진입 시 일부 키워드 검색 결과를 전체 공고처럼 보여주던 문제를 닫기 위한 것이다.

## 변경 배경

기존 화면은 초기 공고 목록을 전체 목록 API가 아니라 키워드 검색 결과 기반으로 구성했다.
그 결과 브라우저에서 기본 상태에 약 33~40건만 노출되고, 필터를 선택하면 이미 제한된 목록 안에서 즉시 필터링되어 0건처럼 보이는 UX 문제가 있었다.

수정 후 책임은 다음처럼 분리했다.

- 기본 진입: `GET /jobs`
- 검색어 검색: `GET /jobs/search`
- 필터 적용: `GET /jobs` query filter
- 초기화: 검색어와 필터를 비우고 `GET /jobs`

## 실행 환경

- API Base URL: `http://localhost:8081/api`
- Gateway: `localhost:8081`
- 검색 키워드: `backend`
- 필터 조건:
  - role: `BACKEND`
  - careerLevel: `JUNIOR`
  - locationRegion: `Seoul`
  - remoteType: `ONSITE`
- page size: `20`

## 검증 명령

```bash
BASE_URL=http://localhost:8081/api \
bash performance/frontend/frontend-job-list-ux-smoke.sh
```

## Smoke Summary

```text
jobs_status=200
jobs_count=20
jobs_search_status=200
jobs_search_count=20
jobs_filter_status=200
jobs_filter_count=13
invalid_role_count=0
invalid_region_count=0
invalid_remote_count=0
```

## 검증 결과

| 항목 | 결과 |
| --- | --- |
| 초기 공고 목록 `GET /jobs` | 성공 |
| 키워드 검색 `GET /jobs/search` | 성공 |
| 필터 적용 `GET /jobs` query filter | 성공 |
| 필터 결과 role mismatch | 0건 |
| 필터 결과 region mismatch | 0건 |
| 필터 결과 remote mismatch | 0건 |

## 주요 확인 사항

### 기본 목록 조회

초기 목록은 키워드 검색이 아니라 `GET /jobs?page=0&size=20` 기준으로 조회된다.
응답은 200이며 20건이 반환되어, 기본 화면이 일부 검색어 결과에 갇히지 않는 것을 확인했다.

### 검색 조회

검색어 `backend` 입력 후 검색 흐름은 `GET /jobs/search?keyword=backend&limit=20`로 분리된다.
응답은 200이며 20건이 반환되어, 키워드 검색은 명시적 검색 동작에서만 수행된다.

### 필터 적용

필터 적용은 `GET /jobs` query filter로 수행된다.
`BACKEND`, `JUNIOR`, `Seoul`, `ONSITE` 조건에서 13건이 반환되었고 role, region, remote mismatch는 모두 0건이었다.

### 프론트 UX 경계

필터 선택 상태와 적용 상태를 분리했다.
사용자가 필터 값을 고르는 동안 목록이 즉시 0건으로 줄지 않고, `필터 적용`을 눌렀을 때만 API 조회 결과가 화면에 반영된다.
적용된 필터 칩을 제거할 때도 남은 필터 기준으로 목록 API를 다시 조회한다.

## 리스크 및 후속 확인

- 현재 프론트는 첫 페이지 기준 목록을 렌더링한다. 무한 스크롤 또는 다음 페이지 탐색은 별도 UX 작업으로 분리할 수 있다.
- 기술 스택, 경험 태그, 마감일 필터는 백엔드 query filter로 직접 보내지 않고 현재 불러온 결과 안에서 보조 필터로 동작한다.
- 브라우저에서 필터 선택, 적용, 칩 제거, 초기화까지 수동 E2E 확인을 추가로 남기면 데모 안정성이 더 좋아진다.
