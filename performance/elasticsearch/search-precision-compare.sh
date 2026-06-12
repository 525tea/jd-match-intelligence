#!/usr/bin/env bash

set -euo pipefail

BEFORE_FILE="${BEFORE_FILE:-}"
AFTER_FILE="${AFTER_FILE:-}"

if [[ -z "${BEFORE_FILE}" || -z "${AFTER_FILE}" ]]; then
  echo "BEFORE_FILE and AFTER_FILE are required." >&2
  echo "Example:" >&2
  echo "  BEFORE_FILE=/tmp/search-before.csv AFTER_FILE=/tmp/search-after.csv bash performance/elasticsearch/search-precision-compare.sh" >&2
  exit 1
fi

if [[ ! -f "${BEFORE_FILE}" ]]; then
  echo "BEFORE_FILE does not exist. path=${BEFORE_FILE}" >&2
  exit 1
fi

if [[ ! -f "${AFTER_FILE}" ]]; then
  echo "AFTER_FILE does not exist. path=${AFTER_FILE}" >&2
  exit 1
fi

extract_summary_value() {
  local file="$1"
  local index="$2"

  awk -F',' -v index="${index}" '$1 == "summary" { print $index }' "${file}" | tail -1 | tr -d '"'
}

before_label="$(extract_summary_value "${BEFORE_FILE}" 2)"
before_total_hits="$(extract_summary_value "${BEFORE_FILE}" 3)"
before_total_relevant="$(extract_summary_value "${BEFORE_FILE}" 4)"
before_precision="$(extract_summary_value "${BEFORE_FILE}" 5)"
before_filtered_out="$(extract_summary_value "${BEFORE_FILE}" 6)"
before_short_query_count="$(extract_summary_value "${BEFORE_FILE}" 7)"

after_label="$(extract_summary_value "${AFTER_FILE}" 2)"
after_total_hits="$(extract_summary_value "${AFTER_FILE}" 3)"
after_total_relevant="$(extract_summary_value "${AFTER_FILE}" 4)"
after_precision="$(extract_summary_value "${AFTER_FILE}" 5)"
after_filtered_out="$(extract_summary_value "${AFTER_FILE}" 6)"
after_short_query_count="$(extract_summary_value "${AFTER_FILE}" 7)"

precision_delta="$(
  awk -v after="${after_precision}" -v before="${before_precision}" \
    'BEGIN { printf "%.4f", after - before }'
)"

relevant_delta="$((after_total_relevant - before_total_relevant))"

cat <<EOF
metric,before_label,before_value,after_label,after_value,delta
precision_at_5,${before_label},${before_precision},${after_label},${after_precision},${precision_delta}
total_hits,${before_label},${before_total_hits},${after_label},${after_total_hits},$((after_total_hits - before_total_hits))
total_relevant,${before_label},${before_total_relevant},${after_label},${after_total_relevant},${relevant_delta}
filtered_out,${before_label},${before_filtered_out},${after_label},${after_filtered_out},$((after_filtered_out - before_filtered_out))
short_query_count,${before_label},${before_short_query_count},${after_label},${after_short_query_count},$((after_short_query_count - before_short_query_count))
EOF
