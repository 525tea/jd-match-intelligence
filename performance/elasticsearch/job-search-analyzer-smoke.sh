#!/usr/bin/env bash

set -euo pipefail

ES_URL="${ES_URL:-http://localhost:9200}"
INDEX_NAME="${INDEX_NAME:-jobflow-jobs}"

curl -sS -X DELETE "${ES_URL}/${INDEX_NAME}" >/dev/null || true

curl -sS -X PUT "${ES_URL}/${INDEX_NAME}" \
  -H "Content-Type: application/json" \
  -d '{
    "settings": {
      "analysis": {
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

analyze "k8s"
analyze "Kubernetes"
analyze "백엔드"
analyze "backend"
analyze "스프링"
analyze "spring boot"
analyze "js"
analyze "javascript"
