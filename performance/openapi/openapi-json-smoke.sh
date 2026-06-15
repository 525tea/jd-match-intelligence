#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
DOCS_PATH="${DOCS_PATH:-/v3/api-docs}"
SWAGGER_UI_PATH="${SWAGGER_UI_PATH:-/swagger-ui/index.html}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required." >&2
  exit 1
fi

response="$(
  curl --fail --silent --show-error \
    "${BASE_URL}${DOCS_PATH}"
)"

curl --fail --silent --show-error \
  --output /dev/null \
  "${BASE_URL}${SWAGGER_UI_PATH}"

success_count="$(
  jq '[
    .info.title == "JobFlow API",
    .components.securitySchemes.bearerAuth.type == "http",
    .components.securitySchemes.bearerAuth.scheme == "bearer",
    .components.securitySchemes.bearerAuth.bearerFormat == "JWT",
    (.paths | has("/auth/login")),
    (.paths | has("/jobs/search")),
    (.paths | has("/projects/{userProjectId}/skills")),
    (.paths | has("/projects/{userProjectId}/experience-tags")),
    (.paths | has("/projects/{userProjectId}/job-matches")),
    (.paths | has("/gap-analysis/projects/{userProjectId}")),
    (.paths | has("/recommendations/jobs"))
  ] | map(select(. == true)) | length' <<< "${response}"
)"

if [[ "${success_count}" -ne 11 ]]; then
  echo "OpenAPI smoke failed. Expected all core API paths and bearerAuth scheme." >&2
  jq '{
    title: .info.title,
    bearerAuth: .components.securitySchemes.bearerAuth,
    paths: (.paths | keys)
  }' <<< "${response}" >&2
  exit 1
fi

echo "BASE_URL=${BASE_URL}"
echo "DOCS_PATH=${DOCS_PATH}"
echo "SWAGGER_UI_PATH=${SWAGGER_UI_PATH}"
echo
echo "OpenAPI smoke summary:"
jq -r '
  "title=" + (.info.title // ""),
  "openapi=" + (.openapi // ""),
  "path_count=" + ((.paths | length) | tostring),
  "has_bearer_auth=" + ((.components.securitySchemes | has("bearerAuth")) | tostring),
  "has_auth_login=" + ((.paths | has("/auth/login")) | tostring),
  "has_jobs_search=" + ((.paths | has("/jobs/search")) | tostring),
  "has_project_skills=" + ((.paths | has("/projects/{userProjectId}/skills")) | tostring),
  "has_project_experience_tags=" + ((.paths | has("/projects/{userProjectId}/experience-tags")) | tostring),
  "has_jd_matches=" + ((.paths | has("/projects/{userProjectId}/job-matches")) | tostring),
  "has_gap_analysis=" + ((.paths | has("/gap-analysis/projects/{userProjectId}")) | tostring),
  "has_recommendations=" + ((.paths | has("/recommendations/jobs")) | tostring)
' <<< "${response}"

echo
echo "OpenAPI JSON smoke completed."
