# Source Display Sections 분리 검증 리포트

작성일: 2026-06-23

범위: 공고 상세 표시용 `description_sections` 분리, WANTED/JUMPIT 상세 렌더링 회귀 검증

## 배경

기존 공고 상세는 `jobs.description` 하나를 검색/매칭/분류용 정규화 텍스트와 화면 표시용 원문 텍스트로 같이 사용했다.

이 구조에서는 source-specific parser가 이미 알고 있던 섹션 구조를 flat string으로 합친 뒤, backend/frontend display layer가 다시 정규식으로 구조를 추론해야 했다. 그 결과 WANTED와 JUMPIT 원문 표시 형식이 반복적으로 깨졌다.

대표 문제는 다음과 같았다.

| 구분 | 증상 | 대표 케이스 |
| --- | --- | --- |
| 문장 중간 middle dot 오인 | `CS · 관제 · 고객`이 `CS`와 `관제` bullet로 쪼개짐 | WANTED 458 |
| 단어 내부 줄바꿈 오인 | `N-layer`, `anti-bot` 계열 문구가 줄바꿈/번호 noise와 섞임 | WANTED 459 |
| 원문 섹션 손실 | JUMPIT `채용절차 및 기타 지원 유의사항`의 하위 제목이 display 단계에서 재가공됨 | JUMPIT 155 |
| 화면 제목 중복 | 구조화된 섹션이 있는데도 `공고 원문` 같은 fallback 제목이 노출됨 | 상세 화면 |

## 구현 방향

`jobs.description`은 기존처럼 검색/매칭/분류용 정규화 텍스트로 유지하고, 화면 표시용 원문 구조는 `jobs.description_sections` JSON으로 분리했다.

source parser는 ingest/replay 시점에 다음 구조를 저장한다.

```json
[
  {
    "type": "RESPONSIBILITIES",
    "title": "주요 업무",
    "body": "source 원문 줄바꿈을 보존한 본문"
  }
]
```

API와 프론트 상세 화면은 `description_sections`가 있으면 이를 우선 렌더링한다. 이로써 display layer가 `jobs.description`을 다시 파싱하지 않아도 된다.

## Raw Replay Backfill

기존 JUMPIT/WANTED row는 raw snapshot replay backfill로 `description_sections`를 다시 생성했다.

실행:

```bash
SOURCES=JUMPIT,WANTED bash performance/collector/backfill-raw-job-description-replay.sh
```

결과:

```text
Raw job description replay backfill completed.
sources=[JUMPIT, WANTED]
processedCount=305
updatedDescriptionCount=0
unchangedDescriptionCount=305
updatedDescriptionSectionsCount=305
updatedRoleCount=0
skippedCount=0
failedCount=0
normalizedSkillJobCount=269
normalizedExperienceTagJobCount=280
```

해석:

- 전체 JUMPIT/WANTED 305건의 display sections가 최신 parser 결과로 재생성됐다.
- raw snapshot 기반 replay가 purge 이후에도 동작했다.
- 이번 변경은 검색/매칭용 `description` 값은 바꾸지 않고, 표시용 `description_sections`만 갱신했다.

## API Smoke

실행:

```bash
BASE_URL=http://127.0.0.1:8081/api bash performance/frontend/frontend-wanted-detail-render-smoke.sh
```

결과:

```text
### WANTED Data Engineer detail
data_engineer_detail_status=200
data_engineer_title=Data Engineer 4~7년
data_engineer_role=DATA_ENGINEER
data_engineer_description_section_count=5

### WANTED middle dot detail
middle_dot_detail_status=200
middle_dot_title=프론트엔드 개발자 (1년~3년이상)
middle_dot_description_section_count=5

### JUMPIT process detail
jumpit_process_detail_status=200
jumpit_process_title=Back-End 경력사원 채용
jumpit_process_description_section_count=8

Frontend WANTED detail render smoke completed.
```

검증한 회귀:

- WANTED 459가 `DATA_ENGINEER`로 유지된다.
- WANTED display sections에 `N\nlayer`, `포함)1`, `****`가 노출되지 않는다.
- WANTED 458에서 `CS · 관제 · 고객`이 새 bullet로 쪼개지지 않는다.
- 구조화된 sections가 있을 때 `공고 원문` fallback 제목이 노출되지 않는다.
- JUMPIT 155의 `채용절차 및 기타 지원 유의사항` 섹션이 `[채용절차]`, `[지원 시 주의사항]` 하위 제목을 보존한다.

## SQL Quality Gate

실행:

```bash
docker compose exec -T mysql mysql -u jobflow -pjobflow --default-character-set=utf8mb4 jobflow < performance/sql/source-display-sections-quality-check.sql
```

요약:

| source | job_count | missing_description_sections_count | inline_middle_dot_split_count | word_internal_split_count | display_noise_count | jumpit_representative_process_missing_count |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| JUMPIT | 150 | 0 | 0 | 0 | 0 | 0 |
| WANTED | 155 | 0 | 0 | 0 | 0 | 0 |

해석:

- JUMPIT/WANTED 전체 305건 모두 `description_sections`를 가진다.
- 대표 WANTED 회귀 패턴인 `CS\n• 관제`, `AI Agent N\nlayer`, `One\nTeam`, `포함)1`, `****`가 저장된 display sections에 남아 있지 않다.
- 대표 JUMPIT 155의 채용절차 하위 제목도 보존됐다.

## 결론

이번 작업으로 검색/매칭용 텍스트와 화면 표시용 원문 구조를 분리했다.

앞으로 공고 상세 화면은 source parser가 저장한 `description_sections`를 그대로 렌더링하고, `jobs.description`은 검색/매칭/분류용으로만 사용한다. 이 구조는 source별 원문 구조를 flat string으로 잃어버린 뒤 regex로 다시 추론하던 기존 문제를 줄인다.

단, WANTED는 API snapshot에 채용절차 필드가 없는 공고가 많다. 이 정보는 현재 snapshot에 존재하지 않는 데이터이므로, 원티드 웹 HTML 상세 보강 수집을 별도 후속 작업으로 다뤄야 한다.
