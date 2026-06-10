#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
LIMIT="${LIMIT:-5}"

csv_escape() {
  local value="$1"
  value="${value//\"/\"\"}"
  printf '"%s"' "${value}"
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

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to run this script."
  exit 1
fi

if ! curl -fsS "${BASE_URL}/actuator/health" >/dev/null; then
  echo "Backend is not reachable at ${BASE_URL}."
  echo "Start backend before running search precision baseline."
  exit 1
fi

# query|expected_roles_csv|expected_keywords_csv
# role 또는 keyword 중 하나라도 맞으면 relevant=true로 계산한다.
QUERIES=(
  "백엔드 개발자|BACKEND|백엔드,backend,java,spring"
  "프론트엔드 React|FRONTEND|프론트엔드,frontend,react,next"
  "쿠버네티스 플랫폼|DEVOPS,SYSTEM_NETWORK|kubernetes,k8s,쿠버네티스,플랫폼,devops,인프라"
  "C++ 개발자|SOFTWARE_ENGINEER,EMBEDDED_SOFTWARE,ROBOT_SOFTWARE,GAME_CLIENT,HARDWARE_ENGINEER|c++,cplusplus,임베디드,로봇,게임,소프트웨어"
  "Node.js 백엔드|BACKEND,FULLSTACK|node,node.js,nodejs,백엔드,backend"
  "데이터 엔지니어|DATA_ENGINEER|데이터 엔지니어,data engineer,kafka,spark,etl"
  "AI 엔지니어|AI_ENGINEER,ML_ENGINEER,GENERATIVE_AI,LLM,COMPUTER_VISION,AI_RESEARCHER|ai,ml,llm,머신러닝,인공지능,딥러닝"
  "보안 엔지니어|SECURITY,SYSTEM_NETWORK|보안,security,network,네트워크"
)

printf "query,rank,id,title,company_name,role,score,relevant\n"

total_hits=0
total_relevant=0

for query_config in "${QUERIES[@]}"; do
  IFS='|' read -r query expected_roles expected_keywords <<< "${query_config}"

  response="$(
    curl -sS -G "${BASE_URL}/jobs/search" \
      --data-urlencode "keyword=${query}" \
      --data-urlencode "limit=${LIMIT}"
  )"

  success="$(echo "${response}" | jq -r '.success')"
  if [[ "${success}" != "true" ]]; then
    echo "Search API failed for query='${query}'. response=${response}" >&2
    exit 1
  fi

  row_count="$(echo "${response}" | jq '.data | length')"

  for (( index = 0; index < row_count; index++ )); do
    rank=$((index + 1))
    id="$(echo "${response}" | jq -r ".data[${index}].id")"
    title="$(echo "${response}" | jq -r ".data[${index}].title")"
    company_name="$(echo "${response}" | jq -r ".data[${index}].companyName")"
    role="$(echo "${response}" | jq -r ".data[${index}].role")"
    location_region="$(echo "${response}" | jq -r ".data[${index}].locationRegion // \"\"")"
    location_city="$(echo "${response}" | jq -r ".data[${index}].locationCity // \"\"")"
    score="$(echo "${response}" | jq -r ".data[${index}].score")"

    haystack="${title} ${company_name} ${location_region} ${location_city}"
    relevant="false"

    if contains_csv_value "${expected_roles}" "${role}" || contains_keyword "${expected_keywords}" "${haystack}"; then
      relevant="true"
      total_relevant=$((total_relevant + 1))
    fi

    total_hits=$((total_hits + 1))

    printf "%s,%s,%s,%s,%s,%s,%s,%s\n" \
      "$(csv_escape "${query}")" \
      "${rank}" \
      "${id}" \
      "$(csv_escape "${title}")" \
      "$(csv_escape "${company_name}")" \
      "${role}" \
      "${score}" \
      "${relevant}"
  done
done

if [[ "${total_hits}" -eq 0 ]]; then
  precision="0.0000"
else
  precision="$(awk -v relevant="${total_relevant}" -v total="${total_hits}" 'BEGIN { printf "%.4f", relevant / total }')"
fi

echo
echo "summary,total_hits,total_relevant,precision_at_${LIMIT}"
echo "summary,${total_hits},${total_relevant},${precision}"
