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
- 사용자 노출 목록: `/jobs` 여러 페이지를 읽어 내부 fixture/운영 검증 데이터를 제외한 실제 공고를 화면에 표시
- 카드 렌더링: 상세 API로 스킬/경험 태그를 보강하고 40개 단위로 점진 렌더링
- 마감 라벨: 마감일이 비어 있는 OPEN 공고는 `상시`로 표시

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

## Browser E2E Summary

프론트 개발 서버(`http://127.0.0.1:5173/#jobs`)에서 실제 화면 흐름을 확인했다.

```text
initial_mode=전체 공고 기준 294개 표시
initial_current_count=294
search_mode=검색 결과 기준 93개 표시
search_current_count=93
filter_mode=필터 결과 기준 74개 표시
filter_current_count=74
reset_mode=전체 공고 기준 294개 표시
reset_current_count=294
empty_state_visible=false
```

## UX 보강 결과

| 항목 | 변경 |
| --- | --- |
| 목록 렌더링 | 전체 공고 풀은 유지하되 화면 렌더링은 40개 단위로 점진 표시 |
| 추가 로딩 | 목록 하단 sentinel 진입 시 다음 40개를 자동 표시하고, `더 보기` 버튼도 제공 |
| 카드 스킬 표시 | 목록/검색/필터 결과에서 상세 공고를 hydrate해 `requiredSkills`, `preferredSkills`, `matched`, `skills` 후보를 카드에 표시 |
| 카드 경험 태그 표시 | 상세 hydrate 결과의 experience tag를 카드 태그 칩으로 표시 |
| 상시 채용 | `deadlineAt`이 비어 있는 OPEN 공고는 `마감 정보 없음` 대신 `상시`로 표시 |
| 마감 배지 톤 | 코랄/빨강 계열에서 회색 구조 톤으로 조정 |

## 검증 결과

| 항목 | 결과 |
| --- | --- |
| 초기 공고 목록 `GET /jobs` | 성공 |
| 키워드 검색 `GET /jobs/search` | 성공 |
| 필터 적용 `GET /jobs` query filter | 성공 |
| 필터 결과 role mismatch | 0건 |
| 필터 결과 region mismatch | 0건 |
| 필터 결과 remote mismatch | 0건 |
| 브라우저 초기 전체 목록 렌더링 | 성공 |
| 브라우저 검색 결과 렌더링 | 성공 |
| 브라우저 필터 적용/초기화 렌더링 | 성공 |
| 프론트 빌드 | 성공 |

## 주요 확인 사항

### 기본 목록 조회

초기 목록은 키워드 검색이 아니라 `GET /jobs?page=0&size=20` 기준으로 조회된다.
응답은 200이며 20건이 반환되어, 기본 화면이 일부 검색어 결과에 갇히지 않는 것을 확인했다.
브라우저 화면에서는 여러 페이지에서 사용자 노출 가능한 공고를 모아 `전체 공고 기준 294개 표시` 상태로 렌더링되는 것을 확인했다.

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

- 현재 프론트는 여러 페이지를 읽어 화면용 목록을 구성하고, 화면 렌더링은 40개 단위로 점진 표시한다. 명시적 페이지네이션 UI는 별도 UX 작업으로 분리할 수 있다.
- 기술 스택, 경험 태그, 마감일 필터는 백엔드 query filter로 직접 보내지 않고 현재 불러온 결과 안에서 보조 필터로 동작한다.
- 브라우저에서 초기 전체 목록, 키워드 검색, 필터 적용, 초기화 흐름은 확인했다. 이번 추가 수정 후에는 빌드와 API 스모크를 재확인했으며, 화면 스크롤 체감은 다음 데모 점검 때 추가 확인한다.
