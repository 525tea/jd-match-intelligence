# Gap Analysis Smoke Artifacts

This directory is reserved for local gap-analysis API smoke outputs.

The script below can write reproducible smoke artifacts here:

```bash
BASE_URL=http://localhost:8080 \
USER_PROJECT_ID=<user_project_id> \
LIMIT=10 \
TARGET_ROLES=BACKEND,FULLSTACK,SOFTWARE_ENGINEER,DEVOPS \
OUTPUT_DIR=docs/metrics/gap-analysis-smoke \
bash performance/analytics/gap-analysis-api-smoke.sh
```

Generated files:

```text
gap-analysis-api-response.json
gap-analysis-match-summary.tsv
```

These generated files are local verification artifacts and are intentionally ignored by git. Copy the key numbers into the worklog or metrics report instead of committing run-specific outputs.
