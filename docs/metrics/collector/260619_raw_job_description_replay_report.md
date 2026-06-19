# Raw Job Description Replay Backfill Report

## 목적

수집 당시 저장한 `jobs.raw_data`를 다시 parser에 통과시켜 `jobs.description`을 재생성하고, 공고 상세 화면에서 읽기 어려웠던 원문 줄바꿈과 섹션 구조를 복구했다.

이번 검증은 다음 범위를 확인했다.

- JUMPIT/WANTED `raw_data` 보존 여부
- description blank 여부
- WANTED raw detail section과 description section 매핑 여부
- JUMPIT description의 섹션/기술스택/본문 줄바꿈 복구 여부
- replay 이후 skill/experience tag relation 존재 여부

## 실행

```bash
bash performance/collector/backfill-raw-job-description-replay.sh
```

DB 검증:

```sql
-- performance/sql/raw-job-description-replay-check.sql
```

## 요약 결과

| Source | Job Count | Missing Raw Data | Blank Description | Zero Skill Jobs | Zero Experience Tag Jobs | Missing Raw Body |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| JUMPIT | 150 | 0 | 0 | 23 | 24 | 0 |
| WANTED | 155 | 0 | 0 | 13 | 1 | 0 |

## WANTED 섹션 복구 결과

| Check | Count |
| --- | ---: |
| Missing intro section | 0 |
| Missing main tasks section | 0 |
| Missing requirements section | 0 |
| Missing preferred section | 0 |
| Missing benefits section | 0 |
| Missing process section | 0 |

WANTED raw detail에 존재하는 주요 섹션은 description에 모두 반영됐다. section miss 상세 쿼리도 `0 rows`를 반환했다.

## JUMPIT description 개선 결과

초기 replay에서는 `GitJavaLinux`, `DockerGraphQLMySQL`, `제목회사명`처럼 inline 텍스트가 붙는 문제가 있었다. parser를 보정해 다음 상태를 확인했다.

- 기술스택이 `Git Java Linux MariaDB MySQL ...`처럼 공백을 유지한다.
- 제목/회사명이 `Back-End 경력사원 채용 하마랩`처럼 분리된다.
- `주요업무`, `자격요건`, `우대사항`, `복지 및 혜택`, `채용절차 및 기타 지원 유의사항`이 줄 단위로 분리된다.
- `[사용기술]`, `[근무조건]`, `[복지 및 문화]` 같은 소제목이 앞 문장에 붙지 않는다.
- 하단 CTA/footer 노이즈인 `기업상세 정보로 이동`, `지원하기`, `스크랩`, `AI 면접 코치` 문구가 제거된다.

예시:

```text
기술스택
Git Java Linux MariaDB MySQL QueryDSL Spring Boot Hibernate REST API

주요업무
• Java & Spring Boot 기반 백엔드 서비스 개발 및 신규 기능 빠른 구현
• REST API 설계, 비즈니스 로직 개발, 시스템 구조 개선

자격요건
• 경력 3년 이상
• Java & Spring Boot 기반 서비스 개발 경험
```

## 남은 관찰 사항

JUMPIT의 일부 공고는 raw 원문 자체가 회사 소개, 복지, 근무지 정보 등을 같은 본문 영역에 포함한다. 이번 작업에서는 공고 원문의 핵심 섹션 구조와 footer noise 제거에 집중했고, 회사 소개 전체 축약이나 섹션별 저장은 후속 데이터 모델 개선 범위로 둔다.

## 결론

raw replay backfill은 정상 동작했다.

- `raw_data` 기반 description 재생성 가능
- source별 parser 재사용 가능
- WANTED section 누락 없음
- JUMPIT 줄바꿈/공백/CTA noise 개선 확인
- replay 이후 skill/tag relation 재정규화 가능
