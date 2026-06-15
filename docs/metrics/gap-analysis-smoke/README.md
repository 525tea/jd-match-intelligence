# Gap Analysis Smoke 산출물

이 디렉터리는 Gap Analysis API smoke 결과를 기록하기 위한 metrics 디렉터리다.

아래 스크립트는 재현 가능한 smoke 산출물을 이 디렉터리에 저장할 수 있다.

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

smoke script는 다음 항목을 검증한다.

- `jobMatches`가 비어 있지 않음
- `jobMatches.length <= LIMIT`
- `LIMIT`은 1 이상 50 이하의 정수
- 응답 role이 `TARGET_ROLES` 안에 있음
- required/preferred skill의 matched/missing detail array가 존재함
- required/preferred match rate는 해당 skill bucket이 비어 있을 때만 `null`
- missing project 요청은 `404`와 `USER_PROJECT_NOT_FOUND`를 반환함

생성 파일:

```text
gap-analysis-api-response.json
gap-analysis-match-summary.tsv
```

위 raw output 파일은 로컬 검증 산출물이다.

PR이나 작업일지에는 raw file을 그대로 커밋하기보다 핵심 숫자만 metrics report에 옮겨 적는다.

최종 결과를 기록할 때는 `REPORT_TEMPLATE.md`를 사용한다.
