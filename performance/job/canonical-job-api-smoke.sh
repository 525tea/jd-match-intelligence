#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081/api}"
SOURCE="${SOURCE:-CANONICAL_SMOKE}"
CANONICAL_FINGERPRINT="${CANONICAL_FINGERPRINT:-canonical-smoke|backend-engineer|seoul}"

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required." >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required." >&2
  exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

assert_equals() {
  local actual="$1"
  local expected="$2"
  local message="$3"

  if [[ "${actual}" != "${expected}" ]]; then
    echo "Assertion failed: ${message}" >&2
    echo "Expected: ${expected}" >&2
    echo "Actual: ${actual}" >&2
    exit 1
  fi
}

assert_json_success() {
  local file="$1"
  local message="$2"

  if ! jq -e '.success == true' "${file}" >/dev/null; then
    echo "Assertion failed: ${message}" >&2
    cat "${file}" >&2
    exit 1
  fi
}

request_status() {
  local path="$1"
  local output_file="$2"

  curl --silent --show-error \
    --request GET \
    --output "${output_file}" \
    --write-out "%{http_code}" \
    "${BASE_URL}${path}"
}

echo "BASE_URL=${BASE_URL}"
echo "SOURCE=${SOURCE}"
echo "CANONICAL_FINGERPRINT=${CANONICAL_FINGERPRINT}"
echo

echo "### GET /jobs"
jobs_response="${tmp_dir}/jobs.json"
jobs_status="$(request_status "/jobs" "${jobs_response}")"

echo "jobs_status=${jobs_status}"
assert_equals "${jobs_status}" "200" "GET /jobs should return 200"
assert_json_success "${jobs_response}" "GET /jobs should return success=true"

job_id="$(jq -r --arg source "${SOURCE}" --arg fingerprint "${CANONICAL_FINGERPRINT}" '
  .data
  | map(select(.source == $source and .canonicalFingerprint == $fingerprint))
  | sort_by(.id)
  | .[0].id // empty
' "${jobs_response}")"

if [[ -z "${job_id}" ]]; then
  echo "Assertion failed: fixture job should exist in GET /jobs response" >&2
  if ! jq -e '.data[0] | has("source") and has("canonicalFingerprint") and has("applyUrl")' "${jobs_response}" >/dev/null; then
    echo "GET /jobs response does not expose source/canonicalFingerprint/applyUrl." >&2
    echo "Rebuild and restart backend after the canonical apply URL code changes:" >&2
    echo "  docker compose up -d --build backend gateway" >&2
    echo "If backend is running from IntelliJ, stop and restart the backend application instead." >&2
  fi
  cat "${jobs_response}" >&2
  exit 1
fi

fixture_job_count="$(jq -r --arg source "${SOURCE}" --arg fingerprint "${CANONICAL_FINGERPRINT}" '
  .data
  | map(select(.source == $source and .canonicalFingerprint == $fingerprint))
  | length
' "${jobs_response}")"

echo "selected_job_id=${job_id}"
echo "fixture_job_count=${fixture_job_count}"

echo
echo "### GET /jobs/${job_id}"
detail_response="${tmp_dir}/job-detail.json"
detail_status="$(request_status "/jobs/${job_id}" "${detail_response}")"

echo "job_detail_status=${detail_status}"
assert_equals "${detail_status}" "200" "GET /jobs/{jobId} should return 200"
assert_json_success "${detail_response}" "GET /jobs/{jobId} should return success=true"

detail_canonical_fingerprint="$(jq -r '.data.canonicalFingerprint' "${detail_response}")"
detail_apply_url="$(jq -r '.data.applyUrl // ""' "${detail_response}")"

assert_equals "${detail_canonical_fingerprint}" "${CANONICAL_FINGERPRINT}" \
  "Job detail should expose canonicalFingerprint"

if [[ -z "${detail_apply_url}" ]]; then
  echo "Assertion failed: Job detail should expose applyUrl" >&2
  cat "${detail_response}" >&2
  exit 1
fi

echo "detail_canonical_fingerprint=${detail_canonical_fingerprint}"
echo "detail_apply_url=${detail_apply_url}"

echo
echo "### GET /jobs/${job_id}/canonical-group"
group_response="${tmp_dir}/canonical-group.json"
group_status="$(request_status "/jobs/${job_id}/canonical-group" "${group_response}")"

echo "canonical_group_status=${group_status}"
assert_equals "${group_status}" "200" "GET /jobs/{jobId}/canonical-group should return 200"
assert_json_success "${group_response}" "GET /jobs/{jobId}/canonical-group should return success=true"

group_fingerprint="$(jq -r '.data.canonicalFingerprint' "${group_response}")"
representative_job_id="$(jq -r '.data.representativeJobId' "${group_response}")"
representative_apply_url="$(jq -r '.data.representativeApplyUrl // ""' "${group_response}")"
duplicate_count="$(jq -r '.data.duplicateCount' "${group_response}")"
group_job_count="$(jq -r '.data.jobs | length' "${group_response}")"
representative_count="$(jq -r '[.data.jobs[] | select(.representative == true)] | length' "${group_response}")"

assert_equals "${group_fingerprint}" "${CANONICAL_FINGERPRINT}" \
  "Canonical group should expose requested fingerprint"
assert_equals "${group_job_count}" "2" "Canonical group should contain both duplicate jobs"
assert_equals "${duplicate_count}" "1" "Canonical group duplicateCount should be one"
assert_equals "${representative_count}" "1" "Canonical group should have exactly one representative"
assert_equals "${representative_apply_url}" "https://company.example.com/jobs/backend-engineer" \
  "Company original URL should be selected as representative apply URL"

if ! jq -e --arg applyUrl "${representative_apply_url}" '
  .data.jobs
  | any(.representative == true and .applyUrl == $applyUrl)
' "${group_response}" >/dev/null; then
  echo "Assertion failed: representative item should match representativeApplyUrl" >&2
  cat "${group_response}" >&2
  exit 1
fi

echo "group_fingerprint=${group_fingerprint}"
echo "representative_job_id=${representative_job_id}"
echo "representative_apply_url=${representative_apply_url}"
echo "duplicate_count=${duplicate_count}"
echo "group_job_count=${group_job_count}"
echo "representative_count=${representative_count}"

echo
echo "### Canonical Job Smoke Summary"
echo "selected_job_id=${job_id}"
echo "fixture_job_count=${fixture_job_count}"
echo "detail_apply_url=${detail_apply_url}"
echo "representative_job_id=${representative_job_id}"
echo "representative_apply_url=${representative_apply_url}"
echo "duplicate_count=${duplicate_count}"
echo "group_job_count=${group_job_count}"

echo
echo "Canonical job API smoke completed."
