#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081/api}"
DATA_ENGINEER_JOB_ID="${DATA_ENGINEER_JOB_ID:-459}"
MIDDLE_DOT_JOB_ID="${MIDDLE_DOT_JOB_ID:-458}"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

echo "BASE_URL=${BASE_URL}"
echo "DATA_ENGINEER_JOB_ID=${DATA_ENGINEER_JOB_ID}"
echo "MIDDLE_DOT_JOB_ID=${MIDDLE_DOT_JOB_ID}"
echo

request_status() {
  local path="$1"
  local output_file="$2"

  curl --silent --show-error \
    --output "${output_file}" \
    --write-out "%{http_code}" \
    --fail-with-body \
    "${BASE_URL}${path}"
}

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

assert_not_contains() {
  local value="$1"
  local needle="$2"
  local message="$3"

  if [[ "${value}" == *"${needle}"* ]]; then
    echo "Assertion failed: ${message}" >&2
    echo "Unexpected text: ${needle}" >&2
    exit 1
  fi
}

extract_render_text() {
  local file="$1"

  jq -r '
    [
      .data.title // "",
      .data.role // "",
      .data.description // "",
      ((.data.descriptionSections // []) | tostring),
      ((.data.skills // []) | tostring),
      ((.data.experienceTags // []) | tostring)
    ] | join("\n")
  ' "${file}"
}

echo "### WANTED Data Engineer detail"
data_engineer_response="${tmp_dir}/wanted-data-engineer-detail.json"
data_engineer_status="$(request_status "/jobs/${DATA_ENGINEER_JOB_ID}" "${data_engineer_response}")"
echo "data_engineer_detail_status=${data_engineer_status}"
assert_equals "${data_engineer_status}" "200" "GET /jobs/${DATA_ENGINEER_JOB_ID} should return 200"
assert_json_success "${data_engineer_response}" "Data Engineer detail should return success=true"

data_engineer_role="$(jq -r '.data.role' "${data_engineer_response}")"
data_engineer_title="$(jq -r '.data.title' "${data_engineer_response}")"
data_engineer_section_count="$(jq -r '(.data.descriptionSections // []) | length' "${data_engineer_response}")"
data_engineer_render_text="$(extract_render_text "${data_engineer_response}")"

echo "data_engineer_title=${data_engineer_title}"
echo "data_engineer_role=${data_engineer_role}"
echo "data_engineer_description_section_count=${data_engineer_section_count}"

assert_equals "${data_engineer_role}" "DATA_ENGINEER" "WANTED Data Engineer title should classify as DATA_ENGINEER"

if [[ "${data_engineer_section_count}" -lt 1 ]]; then
  echo "Assertion failed: WANTED Data Engineer detail should expose description sections" >&2
  cat "${data_engineer_response}" >&2
  exit 1
fi

assert_not_contains "${data_engineer_render_text}" "anti bot" \
  "word-internal antibot should not be split as 'anti bot'"
assert_not_contains "${data_engineer_render_text}" "N layer 아키텍처" \
  "word-internal Nlayer should not be split as 'N layer 아키텍처'"

echo

echo "### WANTED middle dot detail"
middle_dot_response="${tmp_dir}/wanted-middle-dot-detail.json"
middle_dot_status="$(request_status "/jobs/${MIDDLE_DOT_JOB_ID}" "${middle_dot_response}")"
echo "middle_dot_detail_status=${middle_dot_status}"
assert_equals "${middle_dot_status}" "200" "GET /jobs/${MIDDLE_DOT_JOB_ID} should return 200"
assert_json_success "${middle_dot_response}" "Middle dot detail should return success=true"

middle_dot_title="$(jq -r '.data.title' "${middle_dot_response}")"
middle_dot_section_count="$(jq -r '(.data.descriptionSections // []) | length' "${middle_dot_response}")"
middle_dot_render_text="$(extract_render_text "${middle_dot_response}")"

echo "middle_dot_title=${middle_dot_title}"
echo "middle_dot_description_section_count=${middle_dot_section_count}"

if [[ "${middle_dot_section_count}" -lt 1 ]]; then
  echo "Assertion failed: WANTED middle dot detail should expose description sections" >&2
  cat "${middle_dot_response}" >&2
  exit 1
fi

assert_not_contains "${middle_dot_render_text}" "CS • 관제" \
  "inline middle dot should not be converted to bullet after CS"
assert_not_contains "${middle_dot_render_text}" "CS\n• 관제" \
  "inline middle dot should not be split into a new bullet line after CS"

echo

echo "### Frontend WANTED Detail Render Smoke Summary"
echo "data_engineer_detail_status=${data_engineer_status}"
echo "data_engineer_role=${data_engineer_role}"
echo "data_engineer_description_section_count=${data_engineer_section_count}"
echo "middle_dot_detail_status=${middle_dot_status}"
echo "middle_dot_description_section_count=${middle_dot_section_count}"
echo

echo "Frontend WANTED detail render smoke completed."
