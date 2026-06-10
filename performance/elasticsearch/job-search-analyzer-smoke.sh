#!/usr/bin/env bash

set -euo pipefail

ES_URL="${ES_URL:-http://localhost:9200}"
INDEX_NAME="${INDEX_NAME:-jobflow-jobs-analyzer-smoke}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to run this script."
  exit 1
fi

curl -sS -X DELETE "${ES_URL}/${INDEX_NAME}" >/dev/null || true

curl -sS -X PUT "${ES_URL}/${INDEX_NAME}" \
  -H "Content-Type: application/json" \
  -d '{
    "settings": {
      "analysis": {
        "char_filter": {
          "jobflow_tech_stack_normalizer": {
            "type": "mapping",
            "mappings": [
              "ASP.NET => aspnet",
              "Objective-C => objectivec",
              "Node.js => nodejs",
              ".NET => dotnet",
              "C++ => cplusplus",
              "C# => csharp"
            ]
          }
        },
        "filter": {
          "jobflow_tech_synonym": {
            "type": "synonym",
            "synonyms": [
              "k8s, kubernetes",
              "js, javascript",
              "spring, spring boot",
              "백엔드, backend",
              "쿠버네티스, kubernetes",
              "스프링, spring"
            ]
          }
        },
        "analyzer": {
          "jobflow_korean_tech": {
            "type": "custom",
            "tokenizer": "nori_tokenizer",
            "char_filter": [
              "jobflow_tech_stack_normalizer"
            ],
            "filter": [
              "lowercase",
              "jobflow_tech_synonym"
            ]
          }
        }
      }
    },
    "mappings": {
      "properties": {
        "title": {
          "type": "text",
          "analyzer": "jobflow_korean_tech",
          "search_analyzer": "jobflow_korean_tech"
        },
        "description": {
          "type": "text",
          "analyzer": "jobflow_korean_tech",
          "search_analyzer": "jobflow_korean_tech"
        }
      }
    }
  }' >/dev/null

analyze() {
  local text="$1"

  echo
  echo "### ${text}"
  curl -sS -X POST "${ES_URL}/${INDEX_NAME}/_analyze" \
    -H "Content-Type: application/json" \
    -d "{
      \"analyzer\": \"jobflow_korean_tech\",
      \"text\": \"${text}\"
    }" | jq -r '.tokens[].token' | sort -u
}

assert_token() {
  local text="$1"
  local expected_token="$2"

  tokens="$(
    curl -sS -X POST "${ES_URL}/${INDEX_NAME}/_analyze" \
      -H "Content-Type: application/json" \
      -d "{
        \"analyzer\": \"jobflow_korean_tech\",
        \"text\": \"${text}\"
      }" | jq -r '.tokens[].token'
  )"

  if ! echo "${tokens}" | grep -Fx "${expected_token}" >/dev/null; then
    echo "Expected token '${expected_token}' was not found for text '${text}'."
    echo "Actual tokens:"
    echo "${tokens}"
    exit 1
  fi
}

analyze "k8s"
analyze "Kubernetes"
analyze "백엔드"
analyze "backend"
analyze "스프링"
analyze "spring boot"
analyze "js"
analyze "javascript"
analyze "C++"
analyze "C#"
analyze "Node.js"
analyze ".NET"
analyze "ASP.NET"
analyze "Objective-C"

assert_token "C++" "cplusplus"
assert_token "C#" "csharp"
assert_token "Node.js" "nodejs"
assert_token ".NET" "dotnet"
assert_token "ASP.NET" "aspnet"
assert_token "Objective-C" "objectivec"

echo
echo "Analyzer smoke completed."
