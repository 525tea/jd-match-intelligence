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
| Date | TODO |
| DB | local MySQL `jobflow` |
| API base URL | `http://localhost:8080` |
| User | `gap-smoke@example.com` |
| Project external id | `gap-analysis-smoke-project` |
| Target roles | `BACKEND,FULLSTACK,SOFTWARE_ENGINEER,DEVOPS` |
| Limit | `10` |
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
| TODO | TODO | TODO | TODO |

Expected index result:

| Metric | Expected |
| --- | --- |
| JUMPIT indexed open jobs | `> 0` |
| WANTED indexed open jobs | `> 0` |
| target role indexed open jobs | `> 0` |

Actual index result:

| source | open_job_count | indexed_open_job_count | required_indexed_open_job_count | preferred_indexed_open_job_count | indexed_skill_count |
| --- | ---: | ---: | ---: | ---: | ---: |
| JUMPIT | TODO | TODO | TODO | TODO | TODO |
| WANTED | TODO | TODO | TODO | TODO | TODO |

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
| detail fields | one of skill detail fields is missing or not an array |
| target role filter | response contains role outside `TARGET_ROLES` |
| meaningful gap detail | all skill detail lists are empty |
| match rate nullability | required/preferred match rate nullability does not match skill bucket counts |
| missing project guard | missing project request does not return `404` / `USER_PROJECT_NOT_FOUND` |

## Result Summary

| Metric | Actual |
| --- | ---: |
| job match count | TODO |
| top match score | TODO |
| top required match rate | TODO |
| top preferred match rate | TODO |
| response contains target role only | TODO |
| response contains matched/missing skill details | TODO |
| match rate nullability is valid | TODO |
| missing project status | TODO |
| missing project error code | TODO |

## Top Match Samples

Paste the first rows from `gap-analysis-match-summary.tsv`.

| job_id | title | role | required_match_rate | preferred_match_rate | match_score | matched_required_skills | missing_required_skills | matched_preferred_skills | missing_preferred_skills |
| ---: | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- |
| TODO | TODO | TODO | TODO | TODO | TODO | TODO | TODO | TODO | TODO |

## Decision

TODO: PASS or FAIL.

PASS criteria:

- fixture project has 8 project skills
- `job_skill_index` has indexed real JUMPIT/WANTED open jobs
- gap-analysis API returns non-empty matches
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
