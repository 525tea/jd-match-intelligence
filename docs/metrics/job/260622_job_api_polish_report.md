# Job API Polish Report

## 목적

공고 목록/상세 API가 프론트와 운영 검증에서 안전하게 쓰일 수 있도록 다음 항목을 보강했다.

- `/jobs` 전체 조회의 무제한 응답 제거
- 공고 목록 페이지네이션 지원
- 공고 목록 필터 지원
- 공고 생성/수정 시 값 범위 검증
- 공고 수정 시 `job_skills`, `job_experience_tags` 관계 교체 정책 명시 및 구현
- Gateway 기준 API 스모크 검증 추가

## 변경 범위

### 공고 목록 API

기존 `/jobs`는 전체 공고를 정렬해서 반환했다.

변경 후 `/jobs`는 다음 쿼리 파라미터를 받는다.

| 파라미터 | 의미 |
| --- | --- |
| `page` | 0부터 시작하는 페이지 번호 |
| `size` | 페이지 크기 |
| `status` | 공고 상태 |
| `role` | 직무 |
| `careerLevel` | 경력 레벨 |
| `locationRegion` | 지역 |
| `remoteType` | 근무 형태 |

`size`는 과도한 응답을 막기 위해 최대값을 둔다.

### 공고 값 검증

공고 생성/수정 시 다음 비정상 값을 막는다.

| 항목 | 검증 |
| --- | --- |
| 경력 최소값 | 음수 불가 |
| 경력 최대값 | 음수 불가 |
| 경력 범위 | 최소 경력이 최대 경력보다 클 수 없음 |
| 연봉 최소값 | 음수 불가 |
| 연봉 최대값 | 음수 불가 |
| 연봉 범위 | 최소 연봉이 최대 연봉보다 클 수 없음 |
| 채용 인원 | 음수 불가 |
| 오픈/마감일 | 오픈일이 마감일보다 늦을 수 없음 |

### 공고 관계 교체 정책

공고 수정 요청에 `skills`, `experienceTags`가 포함되면 기존 관계를 유지하지 않고 교체한다.

- `skills` 포함: 기존 `job_skills` 삭제 후 요청값으로 재생성
- `experienceTags` 포함: 기존 `job_experience_tags` 삭제 후 요청값으로 재생성
- 필드 미포함: 기존 관계 유지

이 정책은 공고 수정 시 stale relation이 남는 문제를 방지한다.

## 검증 환경

| 항목 | 값 |
| --- | --- |
| API Base URL | `http://localhost:8081/api` |
| 진입점 | Gateway |
| Backend | Docker Compose backend |
| DB | Docker Compose MySQL |
| 검증 스크립트 | `performance/job/job-list-filter-smoke.sh` |

## Gateway API Smoke

실행 명령:

```bash
BASE_URL=http://localhost:8081/api \
bash performance/job/job-list-filter-smoke.sh
```

실행 결과:

```text
BASE_URL=http://localhost:8081/api
PAGE=0
SIZE=5
PAGED_SIZE=1
ROLE=BACKEND
STATUS=OPEN
LOCATION_REGION=Seoul
REMOTE_TYPE=ONSITE

### GET /jobs pagination
jobs_page_status=200
jobs_page_count=1

### GET /jobs role/status filter
jobs_role_status_filter_status=200
jobs_role_status_filter_count=5

### GET /jobs location filter
jobs_location_filter_status=200
jobs_location_filter_count=5

### GET /jobs remote filter
jobs_remote_filter_status=200
jobs_remote_filter_count=5

### GET /jobs validation
jobs_invalid_size_status=400

### Job List Filter Smoke Summary
jobs_page_status=200
jobs_page_count=1
jobs_role_status_filter_status=200
jobs_role_status_filter_count=5
jobs_location_filter_status=200
jobs_location_filter_count=5
jobs_remote_filter_status=200
jobs_remote_filter_count=5
jobs_invalid_size_status=400

Job list filter smoke completed.
```

## 검증 결과

| 검증 항목 | 기대값 | 실제값 | 결과 |
| --- | --- | --- | --- |
| `/jobs?page=0&size=1` | 200, 1건 반환 | 200, 1건 반환 | PASS |
| `/jobs?role=BACKEND&status=OPEN` | 200, 필터 조건 일치 | 200, 5건 반환 | PASS |
| `/jobs?locationRegion=Seoul` | 200, 지역 조건 일치 | 200, 5건 반환 | PASS |
| `/jobs?remoteType=ONSITE` | 200, 근무 형태 조건 일치 | 200, 5건 반환 | PASS |
| `/jobs?size=101` | 400 | 400 | PASS |

## 결론

`/jobs` 목록 API는 더 이상 전체 공고를 무제한 반환하지 않는다.

Gateway 기준으로 페이지네이션, 주요 필터, 요청 검증이 모두 동작하는 것을 확인했다. 공고 수정 시 스킬/경험 태그 관계도 명시적인 교체 정책으로 정리되어, DB relation stale 상태가 남을 위험을 줄였다.

## 후속 확인

- 프론트 공고 목록 화면에서 `/jobs` 페이지네이션/필터 API를 실제로 사용하도록 전환 여부 확인
- k6 부하 테스트에서 `/jobs` 기본 목록 조회와 `/jobs/search` 검색 조회를 분리해 지표 수집
- 공고 수정 관리자 화면이 생기면 relation 교체 정책을 UI 동작과 맞춰 재검증
