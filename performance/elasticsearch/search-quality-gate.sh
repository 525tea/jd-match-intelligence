#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8081/api}"
LIMIT="${LIMIT:-5}"
FETCH_LIMIT="${FETCH_LIMIT:-20}"
MIN_PRECISION="${MIN_PRECISION:-0.80}"
OUTPUT_FILE="${OUTPUT_FILE:-}"

csv_escape() {
  local value="$1"
  value="${value//\"/\"\"}"
  printf '"%s"' "${value}"
}

emit() {
  local line="$1"

  printf "%s\n" "${line}"
  if [[ -n "${OUTPUT_FILE}" ]]; then
    printf "%s\n" "${line}" >> "${OUTPUT_FILE}"
  fi
}

contains_csv_value() {
  local csv="$1"
  local value="$2"

  IFS=',' read -ra values <<< "${csv}"
  for expected in "${values[@]}"; do
    expected="$(echo "${expected}" | xargs)"
    if [[ -n "${expected}" && "${value}" == "${expected}" ]]; then
      return 0
    fi
  done

  return 1
}

contains_keyword() {
  local csv="$1"
  local haystack="$2"

  haystack="$(echo "${haystack}" | tr '[:upper:]' '[:lower:]')"

  IFS=',' read -ra values <<< "${csv}"
  for expected in "${values[@]}"; do
    expected="$(echo "${expected}" | xargs | tr '[:upper:]' '[:lower:]')"
    if [[ -n "${expected}" && "${haystack}" == *"${expected}"* ]]; then
      return 0
    fi
  done

  return 1
}

assert_decimal_greater_or_equal() {
  local actual="$1"
  local expected="$2"
  local message="$3"

  awk -v actual="${actual}" -v expected="${expected}" -v message="${message}" '
    BEGIN {
      if (actual + 0 < expected + 0) {
        printf "Assertion failed: %s\nExpected at least: %.4f\nActual: %.4f\n", message, expected, actual > "/dev/stderr"
        exit 1
      }
    }
  '
}

if [[ -n "${OUTPUT_FILE}" ]]; then
  mkdir -p "$(dirname "${OUTPUT_FILE}")"
  : > "${OUTPUT_FILE}"
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to run this script." >&2
  exit 1
fi

if [[ "${LIMIT}" -lt 1 || "${LIMIT}" -gt 100 ]]; then
  echo "LIMIT must be between 1 and 100. actual=${LIMIT}" >&2
  exit 1
fi

if [[ "${FETCH_LIMIT}" -lt "${LIMIT}" || "${FETCH_LIMIT}" -gt 100 ]]; then
  echo "FETCH_LIMIT must be between LIMIT and 100. actual=${FETCH_LIMIT}, limit=${LIMIT}" >&2
  exit 1
fi

echo "BASE_URL=${BASE_URL}"
echo "LIMIT=${LIMIT}"
echo "FETCH_LIMIT=${FETCH_LIMIT}"
echo "MIN_PRECISION=${MIN_PRECISION}"
echo

# query|expected_roles_csv|expected_keywords_csv
# role 또는 title/company/location keyword 중 하나라도 맞으면 relevant=true로 계산한다.
QUERIES=(
  "backend junior seoul|BACKEND|백엔드,backend,java,spring,seoul,서울"
  "백엔드 개발자|BACKEND|백엔드,backend,java,spring"
  "프론트엔드 React|FRONTEND|프론트엔드,frontend,react,next"
  "쿠버네티스 플랫폼|DEVOPS,SRE,SYSTEM_NETWORK|kubernetes,k8s,쿠버네티스,플랫폼,devops,인프라"
  "C++ 개발자|SOFTWARE_ENGINEER,EMBEDDED_SOFTWARE,ROBOT_SOFTWARE,GAME_CLIENT,HARDWARE_ENGINEER|c++,cplusplus,임베디드,로봇,게임,소프트웨어"
  "Node.js 백엔드|BACKEND,FULLSTACK|node,node.js,nodejs,백엔드,backend"
  "데이터 엔지니어|DATA_ENGINEER,DATA_SCIENTIST,DATA_ANALYST,DEVOPS|데이터 엔지니어,data engineer,데이터 플랫폼,데이터과학,데이터 사이언티스트,데이터 분석가,kafka,spark,etl"
  "AI 엔지니어|AI_ENGINEER,ML_ENGINEER,GENERATIVE_AI,LLM,COMPUTER_VISION,AI_RESEARCHER|ai,ml,llm,머신러닝,인공지능,딥러닝"
  "보안 엔지니어|SECURITY,SYSTEM_NETWORK|보안,security,network,네트워크"
)

emit "query,rank,id,source,title,company_name,role,career_level,location_region,location_city,score,relevant"

total_queries=0
total_hits=0
total_relevant=0
short_query_count=0

for query_config in "${QUERIES[@]}"; do
  IFS='|' read -r keyword expected_roles expected_keywords <<< "${query_config}"
  total_queries=$((total_queries + 1))

  response="$(
    curl -sS -G "${BASE_URL}/jobs/search" \
      --data-urlencode "keyword=${keyword}" \
      --data-urlencode "limit=${FETCH_LIMIT}"
  )"

  success="$(echo "${response}" | jq -r '.success')"
  if [[ "${success}" != "true" ]]; then
    echo "Search API failed for query='${keyword}'. response=${response}" >&2
    exit 1
  fi

  row_count="$(echo "${response}" | jq '.data | length')"
  emitted_rank=0
  query_relevant=0

  for (( index = 0; index < row_count; index++ )); do
    if [[ "${emitted_rank}" -ge "${LIMIT}" ]]; then
      break
    fi

    id="$(echo "${response}" | jq -r ".data[${index}].id")"
    source="$(echo "${response}" | jq -r ".data[${index}].source // \"\"")"
    title="$(echo "${response}" | jq -r ".data[${index}].title")"
    company_name="$(echo "${response}" | jq -r ".data[${index}].companyName")"
    role="$(echo "${response}" | jq -r ".data[${index}].role")"
    career_level="$(echo "${response}" | jq -r ".data[${index}].careerLevel")"
    location_region="$(echo "${response}" | jq -r ".data[${index}].locationRegion // \"\"")"
    location_city="$(echo "${response}" | jq -r ".data[${index}].locationCity // \"\"")"
    score="$(echo "${response}" | jq -r ".data[${index}].score // \"\"")"

    emitted_rank=$((emitted_rank + 1))
    haystack="${title} ${company_name} ${location_region} ${location_city}"
    relevant="false"

    if contains_csv_value "${expected_roles}" "${role}" || contains_keyword "${expected_keywords}" "${haystack}"; then
      relevant="true"
      total_relevant=$((total_relevant + 1))
      query_relevant=$((query_relevant + 1))
    fi

    total_hits=$((total_hits + 1))

    emit "$(csv_escape "${keyword}"),${emitted_rank},${id},${source},$(csv_escape "${title}"),$(csv_escape "${company_name}"),${role},${career_level},$(csv_escape "${location_region}"),$(csv_escape "${location_city}"),${score},${relevant}"
  done

  if [[ "${emitted_rank}" -lt "${LIMIT}" ]]; then
    short_query_count=$((short_query_count + 1))
  fi

  query_precision="$(awk -v relevant="${query_relevant}" -v total="${emitted_rank}" 'BEGIN { if (total == 0) printf "0.0000"; else printf "%.4f", relevant / total }')"
  echo "query='${keyword}' relevant=${query_relevant}/${emitted_rank} precision=${query_precision}"
done

precision="$(awk -v relevant="${total_relevant}" -v total="${total_hits}" 'BEGIN { if (total == 0) printf "0.0000"; else printf "%.4f", relevant / total }')"

emit ""
emit "summary,total_queries,total_hits,total_relevant,precision_at_${LIMIT},short_query_count,fetch_limit"
emit "summary,${total_queries},${total_hits},${total_relevant},${precision},${short_query_count},${FETCH_LIMIT}"

echo
echo "### Search Quality Gate Summary"
echo "total_queries=${total_queries}"
echo "total_hits=${total_hits}"
echo "total_relevant=${total_relevant}"
echo "precision_at_${LIMIT}=${precision}"
echo "short_query_count=${short_query_count}"

assert_decimal_greater_or_equal \
  "${precision}" \
  "${MIN_PRECISION}" \
  "Search Precision@${LIMIT} should be above gate threshold"

echo
echo "Search quality gate completed."
