# Gap Analysis API Smoke Report

## 목적

Gap Analysis API가 다음 데이터를 end-to-end로 연결하는지 확인한다.

- 최신 사용자 프로젝트 skill snapshot
- `job_skill_index` required/preferred skill index
- target role filtering
- 사용자 프로젝트 소유권 / not-found guard
- match score ranking
- matched/missing skill detail response
- 부족 skill에 대한 market evidence response

## 측정 기준

| 항목 | 값 |
| --- | --- |
| 측정일 | `2026-06-12` |
| DB | local MySQL `jobflow` |
| API base URL | `http://localhost:8080` |
| User | smoke fixture user |
| Project external id | smoke fixture project |
| Target roles | `BACKEND,FULLSTACK,SOFTWARE_ENGINEER,DEVOPS` |
| Limit | `10` (`1..50`) |
| Missing project check | `true` |
| Missing project id | `999999999` |
| Fixture SQL | `performance/sql/gap-analysis-smoke-fixture.sql` |
| Check SQL | `performance/sql/gap-analysis-smoke-check.sql` |
| Smoke script | `performance/analytics/gap-analysis-api-smoke.sh` |
| Output dir | `docs/metrics/gap-analysis-smoke` |

## DB Console 사전 확인

먼저 fixture를 실행한다.

```sql
-- performance/sql/gap-analysis-smoke-fixture.sql 전체 실행
```

그 다음 check SQL을 실행한다.

```sql
-- performance/sql/gap-analysis-smoke-check.sql 전체 실행
```

기대 fixture 결과:

| Metric | 기대값 |
| --- | --- |
| `user_project_id` | not null |
| `analysis_id` | not null |
| `user_project_skill_count` | `8` |
| `user_project_skills` | `AWS, Docker, Git, Java, MySQL, Redis, Spring Boot, Spring Framework` |

실제 fixture 결과:

| user_project_id | analysis_id | user_project_skill_count | user_project_skills |
| ---: | ---: | ---: | --- |
| 1 | 1 | 8 | AWS, Docker, Git, Java, MySQL, Redis, Spring Boot, Spring Framework |

기대 index 결과:

| Metric | 기대값 |
| --- | --- |
| JUMPIT indexed open jobs | `> 0` |
| WANTED indexed open jobs | `> 0` |
| target role indexed open jobs | `> 0` |
| preferred-only target role jobs | 기록 대상, `0`일 수 있음 |

실제 index 결과:

| source | open_job_count | indexed_open_job_count | required_indexed_open_job_count | preferred_indexed_open_job_count | indexed_skill_count |
| --- | ---: | ---: | ---: | ---: | ---: |
| JUMPIT | 149 | 126 | 106 | 72 | 585 |
| WANTED | 155 | 142 | 127 | 98 | 776 |

실제 target role bucket 결과:

| source | indexed_target_role_job_count | required_bucket_job_count | preferred_bucket_job_count | preferred_only_job_count | required_only_job_count | both_bucket_job_count |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| JUMPIT | 38 | 35 | 28 | 3 | 10 | 25 |
| WANTED | 59 | 54 | 44 | 5 | 15 | 39 |

Preferred-only sample:

| source | job_id | external_id | title | company_name | role | career_level | required_skill_count | preferred_skill_count | original_url |
| --- | ---: | --- | --- | --- | --- | --- | ---: | ---: | --- |
| WANTED | 346 | 366748 | 통합로그관리 SW 기술지원 엔지니어 | 브레인즈컴퍼니 | BACKEND | MID | 0 | 6 | https://www.wanted.co.kr/wd/366748 |
| WANTED | 276 | 367162 | [반도체장비제조_국내_중견] 반도체 장비 데이터 분석 및 예지진단 SW 개발자 | 히든스카우트(헤드헌팅) | SOFTWARE_ENGINEER | JUNIOR | 0 | 6 | https://www.wanted.co.kr/wd/367162 |
| WANTED | 427 | 367430 | 위성 시험 장비 S/W개발 | 솔탑 | BACKEND | NEWCOMER | 0 | 4 | https://www.wanted.co.kr/wd/367430 |
| WANTED | 453 | 366684 | IT Support Engineer (5년 이상) | 제이와이피엔터테인먼트(JYP) | DEVOPS | MID | 0 | 2 | https://www.wanted.co.kr/wd/366684 |
| WANTED | 338 | 366809 | 백엔드 엔지니어_7년 이상 | 풀림 | BACKEND | MID | 0 | 1 | https://www.wanted.co.kr/wd/366809 |
| JUMPIT | 219 | 54111871 | [AI Vision Engineer] YOLO 기반 다회용기 인식·계수·검수 시스템 개발 담당(경력) | 써큘러랩스 | BACKEND | JUNIOR | 0 | 1 | https://jumpit.saramin.co.kr/position/54111871 |
| JUMPIT | 218 | 54111887 | [AI Vision Engineer] YOLO 기반 다회용기 인식·계수·검수 시스템 개발 담당 | 써큘러랩스 | BACKEND | NEWCOMER | 0 | 1 | https://jumpit.saramin.co.kr/position/54111887 |
| JUMPIT | 205 | 54118057 | ML Systems Runtime Engineer [신입] | 보스반도체 | BACKEND | NEWCOMER | 0 | 1 | https://jumpit.saramin.co.kr/position/54118057 |

## API Smoke 실행 명령

```bash
BASE_URL=http://localhost:8080 \
USER_PROJECT_ID=<user_project_id> \
LIMIT=10 \
TARGET_ROLES=BACKEND,FULLSTACK,SOFTWARE_ENGINEER,DEVOPS \
EXPECT_MISSING_PROJECT_CHECK=true \
MISSING_PROJECT_ID=999999999 \
OUTPUT_DIR=docs/metrics/gap-analysis-smoke \
bash performance/analytics/gap-analysis-api-smoke.sh
```

기대 결과:

```text
Gap analysis API smoke completed.
### GET /gap-analysis/projects/999999999 should return USER_PROJECT_NOT_FOUND
Saved response: docs/metrics/gap-analysis-smoke/gap-analysis-api-response.json
Saved match summary: docs/metrics/gap-analysis-smoke/gap-analysis-match-summary.tsv
```

## API Smoke 검증 항목

smoke script는 다음 조건 중 하나라도 깨지면 실패한다.

| 검증 항목 | 실패 조건 |
| --- | --- |
| API success | `.success != true` |
| 비어 있지 않은 결과 | `.data.jobMatches`가 비어 있음 |
| limit contract | `.data.jobMatches.length`가 요청한 `LIMIT`을 초과함 |
| detail fields | skill detail field가 없거나 array가 아님 |
| target role filter | 응답에 `TARGET_ROLES` 밖의 role이 포함됨 |
| meaningful gap detail | 모든 skill detail list가 비어 있음 |
| match rate nullability | required/preferred match rate nullability가 skill bucket count와 맞지 않음 |
| evidence contract | evidence object 또는 evidence array가 없거나 형식이 잘못됨 |
| meaningful evidence | 모든 match의 evidence가 비어 있음 |
| missing project guard | missing project 요청이 `404` / `USER_PROJECT_NOT_FOUND`를 반환하지 않음 |

## Evidence 확장

Gap Analysis 응답은 각 job match에 `evidence` object를 포함한다.

| 항목 | 값 |
| --- | --- |
| Evidence 확장 측정일 | `2026-06-15` |

`evidence` object는 다음 정보를 포함한다.

- `addedJobs`: 부족 skill 기준 최신 월간 `skill_trends.job_count` 합계
- `cooccurrences`: 부족 skill과 함께 등장한 `skill_cooccurrence` row
- `relatedTags`: 부족 skill과 연결된 `skill_experience_market` row
- `learningConnections`: 부족 skill과 market evidence를 기반으로 만든 사용자 설명

Support threshold:

| Evidence source | 최소 support |
| --- | ---: |
| `skill_cooccurrence` | `cooccurrence_count >= 3` |
| `skill_experience_market` | `job_count >= 3` |

Evidence smoke 요약:

| Metric | 실제값 |
| --- | ---: |
| meaningful evidence match count | 8 |
| evidence added jobs sum | 1149 |
| cooccurrence evidence count | 40 |
| related tag evidence count | 39 |
| learning connection count | 53 |

해석:

- 반환된 10개 match 중 8개가 비어 있지 않은 evidence를 가졌다.
- required/preferred skill이 모두 충족된 match는 설명할 missing skill이 없으므로 evidence가 비어 있을 수 있다.
- evidence count를 통해 Gap Analysis가 단순 missing skill list가 아니라 analytics aggregate table을 사용함을 확인했다.

Evidence source 준비 상태:

| Source | 기대값 |
| --- | --- |
| `skill_trends` | latest monthly period exists and has rows |
| `skill_cooccurrence` | latest monthly period exists and supported rows exist |
| `skill_experience_market` | latest monthly period exists and supported rows exist |

## 최종 DB Match Baseline

이 섹션은 Gap Analysis query/scoring hardening 이후 DB Console 기준 baseline을 기록한다.
`performance/sql/job-skill-index-match-smoke.sql`로 API response layer에 들어가기 전 `job_skill_index`가 설명 가능한 required/preferred hit-miss row를 만들 수 있는지 확인했다.

SQL baseline은 다음을 검증한다.

- required skill과 preferred skill을 분리해서 점수화한다.
- 공고별 matched/missing skill name을 설명할 수 있다.
- required 또는 preferred bucket이 없는 공고는 해당 match rate를 nullable로 유지한다.
- `match_score`는 단일 flat skill count가 아니라 required/preferred match rate에서 계산된다.

상위 DB baseline row:

| source | job_id | external_id | title | role | required_match_rate | preferred_match_rate | match_score | matched_required_skills | missing_required_skills | matched_preferred_skills | missing_preferred_skills |
| --- | ---: | --- | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- |
| WANTED | 267 | 367233 | 백엔드 개발자 (Java/팀원) | BACKEND | 100.00 | 66.67 | 90.00 | Java, Spring Boot, Spring Framework |  | AWS, Git | Jenkins |
| WANTED | 441 | 367362 | [Senior] Software Engineer | FRONTEND | 100.00 | 14.29 | 74.29 | AWS |  | Redis | FastAPI, Next.js, Python, React, Tailwind CSS, Terraform |
| JUMPIT | 201 | 54118135 | 개발매니저(PM) | PM | 100.00 | 0.00 | 70.00 | Spring Boot |  |  | React |
| WANTED | 431 | 367407 | 더아파트팀 세무회계파트 백엔드 개발자 | BACKEND | 40.00 | 100.00 | 58.00 | Java, Spring Boot | MyBatis, Oracle Database, SQL | Git |  |
| JUMPIT | 184 | 54124332 | 웹어플리케이션 백엔드 개발자(2년↑) | BACKEND | 66.67 | 25.00 | 54.17 | Java, MySQL, Spring Boot, Spring Framework | Linux, Oracle Database | Docker | Hibernate, Kubernetes, Python |
| WANTED | 293 | 367080 | Python 백엔드 개발자 1~5년 | BACKEND | 33.33 | 100.00 | 53.33 | Git | Python, REST API | Docker |  |
| WANTED | 426 | 367434 | .Net 개발자 | BACKEND | 50.00 | 50.00 | 50.00 | Java | C# | Git | Software Engineering |
| JUMPIT | 155 | 54124188 | Back-End 경력사원 채용 | BACKEND | 50.00 | 42.86 | 47.86 | Git, Java, MySQL, Spring Boot | Hibernate, Linux, MariaDB, QueryDSL | AWS, Docker, Redis | Kafka, Kubernetes, RabbitMQ, Spring Security |
| WANTED | 340 | 366799 | Data Infra Engineer | BACKEND | 66.67 | 0.00 | 46.67 | Docker, Java | Python |  | Apache HTTP Server, Kafka, MongoDB |
| WANTED | 344 | 366775 | 백엔드개발 (Predict) | BACKEND | 50.00 | 33.33 | 45.00 | Java, MySQL, Spring Boot | Jira, Linux, Notion | Docker | Kubernetes, MSA |

## 결과 요약

| Metric | 실제값 |
| --- | ---: |
| job match count | 10 |
| job match count <= limit | PASS |
| top match score | 92.67 |
| top required match rate | 100.00 |
| top preferred match rate | 66.67 |
| response contains target role only | PASS |
| response contains matched/missing skill details | PASS |
| match rate nullability is valid | PASS |
| evidence fields are present | PASS |
| evidence contains meaningful rows | PASS |
| meaningful evidence match count | 8 |
| evidence added jobs sum | 1149 |
| cooccurrence evidence count | 40 |
| related tag evidence count | 39 |
| learning connection count | 53 |
| missing project status | 404 |
| missing project error code | USER_PROJECT_NOT_FOUND |

## Top Match Sample

`gap-analysis-match-summary.tsv`의 상위 row를 기록한다.

| job_id | title | role | required_match_rate | preferred_match_rate | match_score | matched_required_skills | missing_required_skills | matched_preferred_skills | missing_preferred_skills |
| ---: | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- |
| 267 | 백엔드 개발자 (Java/팀원) | BACKEND | 100.00 | 66.67 | 92.67 | Java, Spring Boot, Spring Framework |  | AWS, Git | Jenkins |
| 350 | 구인구직 플랫폼 풀스택 개발자 (5년 이상) | FULLSTACK | 50.00 | 0.00 | 81.00 | AWS, Docker, Git, Java, MySQL, Redis, Spring Boot, Spring Framework | Flutter, Hibernate, JavaScript, Linux, MariaDB, QueryDSL, Spring Security, TypeScript |  | ISMS, React |
| 18 | 백엔드 성능 개선 엔지니어 | BACKEND | 100.00 | 100.00 | 73.00 | Spring Boot |  | MySQL |  |
| 16 | 백엔드 플랫폼 개발자 | BACKEND | 100.00 | 100.00 | 73.00 | Spring Boot |  | Redis |  |
| 184 | 웹어플리케이션 백엔드 개발자(2년↑) | BACKEND | 66.67 | 25.00 | 72.83 | Java, MySQL, Spring Boot, Spring Framework | Linux, Oracle Database | Docker | Hibernate, Kubernetes, Python |

## 판정

PASS.

PASS 기준:

- fixture project가 8개 project skill을 가진다.
- `job_skill_index`에 real JUMPIT/WANTED open job이 색인되어 있다.
- Gap Analysis API가 비어 있지 않은 match 결과를 반환한다.
- Gap Analysis API가 요청한 `LIMIT` 이하의 결과만 반환한다.
- missing project 요청이 `404`와 `USER_PROJECT_NOT_FOUND`를 반환한다.
- 반환된 match가 요청한 target role 안에 있다.
- 응답에 matched/missing required/preferred skill detail이 포함된다.
- match rate는 해당 required/preferred skill bucket이 비어 있을 때만 `null`이다.

## 남은 리스크

| 리스크 | 현재 처리 | 후속 확인 |
| --- | --- | --- |
| match score weight가 heuristic이다 | smoke는 ranking 품질 최종판이 아니라 정렬과 설명 가능성을 검증한다 | labeled fixture 기준으로 score weight 튜닝 |
| project skill snapshot이 fixture 기반이다 | smoke는 deterministic static skill list를 사용한다 | 실제 GitHub analysis sample로 교체 |
| missing project smoke가 synthetic high id를 사용한다 | not-found guard는 검증하지만, 다른 실제 사용자 소유 project 접근은 검증하지 않는다 | multi-user smoke data 도입 시 cross-user ownership fixture 추가 |
| required/preferred 추출은 JD section parsing 품질에 의존한다 | 현재 index는 section을 분리하지만 source text 품질에 따라 달라질 수 있다 | required/preferred 분포를 계속 측정 |
| raw smoke file은 local-only다 | 공개 report에는 핵심 row와 판정만 기록하고, 생성 JSON/TSV는 커밋하지 않는다 | API smoke 재실행 시 raw file은 로컬에서 재생성 |
| evidence 문구는 heuristic이다 | learning connection copy는 개인화 curriculum이 아니라 aggregate evidence 기반 설명이다 | 최종 추천 문구가 아니라 guidance/hint 톤으로 유지 |
| fully matched job은 evidence가 비어 있을 수 있다 | missing skill이 없으면 empty evidence가 정상이다 | frontend는 empty evidence panel을 숨긴다 |
