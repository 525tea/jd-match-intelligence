#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081/api}"
DOCS_PATH="${DOCS_PATH:-/v3/api-docs}"
SWAGGER_UI_PATH="${SWAGGER_UI_PATH:-/swagger-ui/index.html}"

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required." >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required." >&2
  exit 1
fi

assert_json_true() {
  local expression="$1"
  local message="$2"

  if ! jq -e "${expression}" >/dev/null <<< "${openapi_json}"; then
    echo "Assertion failed: ${message}" >&2
    echo "Expression: ${expression}" >&2
    echo >&2
    echo "OpenAPI summary:" >&2
    jq '{
      title: .info.title,
      openapi: .openapi,
      securitySchemes: .components.securitySchemes,
      paths: (.paths | keys)
    }' <<< "${openapi_json}" >&2
    exit 1
  fi
}

assert_path_method() {
  local path="$1"
  local method="$2"
  local method_label

  method_label="$(printf '%s' "${method}" | tr '[:lower:]' '[:upper:]')"

  assert_json_true \
    ".paths[\"${path}\"] | has(\"${method}\")" \
    "OpenAPI should expose ${method_label} ${path}"
}

echo "BASE_URL=${BASE_URL}"
echo "DOCS_PATH=${DOCS_PATH}"
echo "SWAGGER_UI_PATH=${SWAGGER_UI_PATH}"
echo

openapi_json="$(
  curl --fail --silent --show-error \
    "${BASE_URL}${DOCS_PATH}"
)"

curl --fail --silent --show-error \
  --output /dev/null \
  "${BASE_URL}${SWAGGER_UI_PATH}"

assert_json_true '.info.title == "JobFlow API"' "OpenAPI title should be JobFlow API"
assert_json_true '.components.securitySchemes.bearerAuth.type == "http"' "bearerAuth type should be http"
assert_json_true '.components.securitySchemes.bearerAuth.scheme == "bearer"' "bearerAuth scheme should be bearer"
assert_json_true '.components.securitySchemes.bearerAuth.bearerFormat == "JWT"' "bearerAuth format should be JWT"

assert_path_method "/auth/login" "post"
assert_path_method "/auth/signup" "post"
assert_path_method "/auth/me" "get"
assert_path_method "/jobs/search" "get"
assert_path_method "/jobs/{jobId}" "get"
assert_path_method "/user/jobs/{jobId}/view" "post"
assert_path_method "/user/jobs/{jobId}/save" "post"
assert_path_method "/user/jobs/{jobId}/save" "delete"
assert_path_method "/user/jobs/{jobId}/ignore" "post"
assert_path_method "/user/jobs/{jobId}/ignore" "delete"
assert_path_method "/user/jobs/saved" "get"
assert_path_method "/user/jobs/ignored" "get"
assert_path_method "/user/jobs/viewed" "get"
assert_path_method "/applications" "get"
assert_path_method "/applications" "post"
assert_path_method "/applications/{applicationId}" "get"
assert_path_method "/applications/{applicationId}/status" "patch"
assert_path_method "/applications/{applicationId}/status-histories" "get"
assert_path_method "/projects/{userProjectId}/skills" "get"
assert_path_method "/projects/{userProjectId}/experience-tags" "get"
assert_path_method "/projects/{userProjectId}/job-matches" "get"
assert_path_method "/gap-analysis/projects/{userProjectId}" "get"
assert_path_method "/recommendations/jobs" "get"
assert_path_method "/trends/skills" "get"

echo "### OpenAPI Contract Summary"
jq -r '
  "title=" + (.info.title // ""),
  "openapi=" + (.openapi // ""),
  "path_count=" + ((.paths | length) | tostring),
  "has_bearer_auth=" + ((.components.securitySchemes | has("bearerAuth")) | tostring),
  "operation_level_security_count=" + ([
    .paths[]
    | to_entries[]
    | select(.value.security != null)
  ] | length | tostring),
  "has_auth_login=" + ((.paths | has("/auth/login")) | tostring),
  "has_auth_me=" + ((.paths | has("/auth/me")) | tostring),
  "has_jobs_search=" + ((.paths | has("/jobs/search")) | tostring),
  "has_job_detail=" + ((.paths | has("/jobs/{jobId}")) | tostring),
  "has_user_job_view=" + ((.paths | has("/user/jobs/{jobId}/view")) | tostring),
  "has_user_job_save=" + ((.paths | has("/user/jobs/{jobId}/save")) | tostring),
  "has_user_job_unsave=" + (((.paths["/user/jobs/{jobId}/save"] // {}) | has("delete")) | tostring),
  "has_user_job_ignore=" + ((.paths | has("/user/jobs/{jobId}/ignore")) | tostring),
  "has_user_job_unignore=" + (((.paths["/user/jobs/{jobId}/ignore"] // {}) | has("delete")) | tostring),
  "has_applications=" + ((.paths | has("/applications")) | tostring),
  "has_application_detail=" + ((.paths | has("/applications/{applicationId}")) | tostring),
  "has_application_status_update=" + (((.paths["/applications/{applicationId}/status"] // {}) | has("patch")) | tostring),
  "has_application_status_histories=" + (((.paths["/applications/{applicationId}/status-histories"] // {}) | has("get")) | tostring),
  "has_project_skills=" + ((.paths | has("/projects/{userProjectId}/skills")) | tostring),
  "has_project_experience_tags=" + ((.paths | has("/projects/{userProjectId}/experience-tags")) | tostring),
  "has_jd_matches=" + ((.paths | has("/projects/{userProjectId}/job-matches")) | tostring),
  "has_gap_analysis=" + ((.paths | has("/gap-analysis/projects/{userProjectId}")) | tostring),
  "has_recommendations=" + ((.paths | has("/recommendations/jobs")) | tostring),
  "has_trend_skills=" + ((.paths | has("/trends/skills")) | tostring)
' <<< "${openapi_json}"

echo
echo "OpenAPI contract smoke completed."
