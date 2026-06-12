# Gap Analysis Smoke Artifacts

This directory is reserved for local gap-analysis API smoke outputs.

The script below can write reproducible smoke artifacts here:

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

The smoke script verifies:

- non-empty `jobMatches`
- `jobMatches.length <= LIMIT`
- `LIMIT` is an integer from 1 to 50
- response roles are inside `TARGET_ROLES`
- matched/missing required/preferred skill detail arrays exist
- required/preferred match rates are `null` only when the matching skill bucket is empty
- missing project requests return `404` with `USER_PROJECT_NOT_FOUND`

Generated files:

```text
gap-analysis-api-response.json
gap-analysis-match-summary.tsv
```

These generated files are local verification artifacts and are intentionally ignored by git. Copy the key numbers into the worklog or metrics report instead of committing run-specific outputs.

Use `REPORT_TEMPLATE.md` when recording the final result for a PR or worklog.
