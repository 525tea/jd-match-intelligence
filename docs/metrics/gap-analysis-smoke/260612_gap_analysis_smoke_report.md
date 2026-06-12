# Gap Analysis API Smoke Report

## Purpose

This report records whether the gap-analysis API can connect these pieces end to end:

- latest user project skill snapshot
- `job_skill_index` required/preferred skill index
- target role filtering
- user project ownership/not-found guard
- match score ranking
- matched/missing skill detail response

## Measurement Setup

| Item | Value |
| --- | --- |
| Date | `2026-06-12` |
| DB | local MySQL `jobflow` |
| API base URL | `http://localhost:8080` |
| User | `gap-smoke@example.com` |
| Project external id | `gap-analysis-smoke-project` |
| Target roles | `BACKEND,FULLSTACK,SOFTWARE_ENGINEER,DEVOPS` |
| Limit | `10` (`1..50`) |
| Missing project check | `true` |
| Missing project id | `999999999` |
| Fixture SQL | `performance/sql/gap-analysis-smoke-fixture.sql` |
| Check SQL | `performance/sql/gap-analysis-smoke-check.sql` |
| Smoke script | `performance/analytics/gap-analysis-api-smoke.sh` |
| Output dir | `docs/metrics/gap-analysis-smoke` |

## DB Console Checks

Run the fixture first:

```sql
-- performance/sql/gap-analysis-smoke-fixture.sql 전체 실행
```

Then run:

```sql
-- performance/sql/gap-analysis-smoke-check.sql 전체 실행
```

Expected fixture result:

| Metric | Expected |
| --- | --- |
| `user_project_id` | not null |
| `analysis_id` | not null |
| `user_project_skill_count` | `8` |
| `user_project_skills` | `AWS, Docker, Git, Java, MySQL, Redis, Spring Boot, Spring Framework` |

Actual fixture result:

| user_project_id | analysis_id | user_project_skill_count | user_project_skills |
| ---: | ---: | ---: | --- |
| 1 | 1 | 8 | AWS, Docker, Git, Java, MySQL, Redis, Spring Boot, Spring Framework |

Expected index result:

| Metric | Expected |
| --- | --- |
| JUMPIT indexed open jobs | `> 0` |
| WANTED indexed open jobs | `> 0` |
| target role indexed open jobs | `> 0` |
| preferred-only target role jobs | recorded, may be `0` |

Actual index result:

| source | open_job_count | indexed_open_job_count | required_indexed_open_job_count | preferred_indexed_open_job_count | indexed_skill_count |
| --- | ---: | ---: | ---: | ---: | ---: |
| JUMPIT | 149 | 126 | 106 | 72 | 585 |
| WANTED | 155 | 142 | 127 | 98 | 776 |

Actual target role bucket result:

| source | indexed_target_role_job_count | required_bucket_job_count | preferred_bucket_job_count | preferred_only_job_count | required_only_job_count | both_bucket_job_count |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| JUMPIT | 38 | 35 | 28 | 3 | 10 | 25 |
| WANTED | 59 | 54 | 44 | 5 | 15 | 39 |

Preferred-only samples:

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

## API Smoke Command

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

Expected result:

```text
Gap analysis API smoke completed.
### GET /gap-analysis/projects/999999999 should return USER_PROJECT_NOT_FOUND
Saved response: docs/metrics/gap-analysis-smoke/gap-analysis-api-response.json
Saved match summary: docs/metrics/gap-analysis-smoke/gap-analysis-match-summary.tsv
```

## API Smoke Assertions

The smoke script fails when any of these conditions are true:

| Assertion | Failure condition |
| --- | --- |
| API success | `.success != true` |
| non-empty result | `.data.jobMatches` is empty |
| limit contract | `.data.jobMatches.length` exceeds requested `LIMIT` |
| detail fields | one of skill detail fields is missing or not an array |
| target role filter | response contains role outside `TARGET_ROLES` |
| meaningful gap detail | all skill detail lists are empty |
| match rate nullability | required/preferred match rate nullability does not match skill bucket counts |
| missing project guard | missing project request does not return `404` / `USER_PROJECT_NOT_FOUND` |

## Result Summary

| Metric | Actual |
| --- | ---: |
| job match count | 10 |
| job match count <= limit | PASS |
| top match score | 92.67 |
| top required match rate | 100.00 |
| top preferred match rate | 66.67 |
| response contains target role only | PASS |
| response contains matched/missing skill details | PASS |
| match rate nullability is valid | PASS |
| missing project status | 404 |
| missing project error code | USER_PROJECT_NOT_FOUND |

## Top Match Samples

Paste the first rows from `gap-analysis-match-summary.tsv`.

| job_id | title | role | required_match_rate | preferred_match_rate | match_score | matched_required_skills | missing_required_skills | matched_preferred_skills | missing_preferred_skills |
| ---: | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- |
| 267 | 백엔드 개발자 (Java/팀원) | BACKEND | 100.00 | 66.67 | 92.67 | Java, Spring Boot, Spring Framework |  | AWS, Git | Jenkins |
| 350 | 구인구직 플랫폼 풀스택 개발자 (5년 이상) | FULLSTACK | 50.00 | 0.00 | 81.00 | AWS, Docker, Git, Java, MySQL, Redis, Spring Boot, Spring Framework | Flutter, Hibernate, JavaScript, Linux, MariaDB, QueryDSL, Spring Security, TypeScript |  | ISMS, React |
| 18 | 백엔드 성능 개선 엔지니어 | BACKEND | 100.00 | 100.00 | 73.00 | Spring Boot |  | MySQL |  |
| 16 | 백엔드 플랫폼 개발자 | BACKEND | 100.00 | 100.00 | 73.00 | Spring Boot |  | Redis |  |
| 184 | 웹어플리케이션 백엔드 개발자(2년↑) | BACKEND | 66.67 | 25.00 | 72.83 | Java, MySQL, Spring Boot, Spring Framework | Linux, Oracle Database | Docker | Hibernate, Kubernetes, Python |

## Decision

PASS.

PASS criteria:

- fixture project has 8 project skills
- `job_skill_index` has indexed real JUMPIT/WANTED open jobs
- gap-analysis API returns non-empty matches
- gap-analysis API returns no more than requested `LIMIT`
- missing project request returns `404` with `USER_PROJECT_NOT_FOUND`
- returned matches are restricted to requested target roles
- response includes matched/missing required/preferred skill details
- match rate is `null` only when the corresponding required/preferred skill bucket is empty

## Open Risks

| Risk | Current handling | Follow-up |
| --- | --- | --- |
| match score weights are heuristic | Smoke verifies ordering and explainability, not final ranking quality | tune with labeled fixture after W5 matching engine |
| project skill snapshot is fixture-based | Smoke uses deterministic static skill list | replace with real GitHub analysis sample in W5 |
| missing project smoke uses a high synthetic id | Smoke verifies not-found guard, not cross-user ownership with another real account | add cross-user ownership fixture when multi-user smoke data is introduced |
| required/preferred extraction depends on JD section parsing | Current index separates sections but quality varies by source text | keep measuring required/preferred distribution |
