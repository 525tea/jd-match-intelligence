#!/usr/bin/env bash

set -euo pipefail

ES_URL="${ES_URL:-http://localhost:9200}"
INDEX_NAME="${INDEX_NAME:-jobflow-jobs}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to run this script."
  exit 1
fi

if ! curl -fsS "${ES_URL}/${INDEX_NAME}" >/dev/null; then
  echo "Elasticsearch index '${INDEX_NAME}' does not exist."
  echo "Start backend first so JobSearchIndexInitializer can create the index."
  exit 1
fi

curl -sS -X POST "${ES_URL}/${INDEX_NAME}/_delete_by_query?conflicts=proceed&refresh=true" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "term": {
        "source": "W3_8_QUALITY"
      }
    }
  }' >/dev/null

curl -sS -X POST "${ES_URL}/${INDEX_NAME}/_bulk?refresh=true" \
  -H "Content-Type: application/x-ndjson" \
  --data-binary @- >/dev/null <<'NDJSON'
{"index":{"_id":"910001"}}
{"id":"910001","source":"W3_8_QUALITY","externalId":"kubernetes-platform-engineer","canonicalFingerprint":"w3-8-kubernetes-platform-engineer","title":"Kubernetes 플랫폼 엔지니어","companyName":"Example Cloud","description":"Kubernetes 기반 컨테이너 플랫폼과 클러스터 운영 자동화를 담당합니다.","role":"DEVOPS","roleDetail":"Kubernetes Platform","careerLevel":"ANY","employmentType":"FULL_TIME","industry":"IT","locationCountry":"KR","locationRegion":"Seoul","locationCity":"Gangnam","remoteType":"HYBRID","deadlineAt":null,"createdAt":"2026-06-05T00:00:00","updatedAt":"2026-06-05T00:00:00"}
{"index":{"_id":"910002"}}
{"id":"910002","source":"W3_8_QUALITY","externalId":"k8s-platform-engineer","canonicalFingerprint":"w3-8-k8s-platform-engineer","title":"k8s 플랫폼 엔지니어","companyName":"Example Infra","description":"k8s 기반 배포 자동화와 서비스 운영 환경을 개선합니다.","role":"DEVOPS","roleDetail":"k8s Platform","careerLevel":"ANY","employmentType":"FULL_TIME","industry":"IT","locationCountry":"KR","locationRegion":"Seoul","locationCity":"Gangnam","remoteType":"HYBRID","deadlineAt":null,"createdAt":"2026-06-05T00:00:00","updatedAt":"2026-06-05T00:00:00"}
{"index":{"_id":"910003"}}
{"id":"910003","source":"W3_8_QUALITY","externalId":"backend-spring-engineer","canonicalFingerprint":"w3-8-backend-spring-engineer","title":"백엔드 개발자","companyName":"Example Backend","description":"Spring Boot 기반 API 서버와 MySQL 데이터 모델을 개발합니다.","role":"BACKEND","roleDetail":"Java Spring","careerLevel":"ANY","employmentType":"FULL_TIME","industry":"IT","locationCountry":"KR","locationRegion":"Seoul","locationCity":"Mapo","remoteType":"ONSITE","deadlineAt":null,"createdAt":"2026-06-05T00:00:00","updatedAt":"2026-06-05T00:00:00"}
{"index":{"_id":"910004"}}
{"id":"910004","source":"W3_8_QUALITY","externalId":"deadline-urgent-backend","canonicalFingerprint":"w3-8-deadline-urgent-backend","title":"백엔드 플랫폼 개발자","companyName":"Example Deadline A","description":"Spring Boot 백엔드 플랫폼 API 개발을 담당합니다.","role":"BACKEND","roleDetail":"Java Spring","careerLevel":"ANY","employmentType":"FULL_TIME","industry":"IT","locationCountry":"KR","locationRegion":"Seoul","locationCity":"Yeongdeungpo","remoteType":"ONSITE","deadlineAt":"2026-06-06T23:59:00","createdAt":"2026-06-01T00:00:00","updatedAt":"2026-06-01T00:00:00"}
{"index":{"_id":"910005"}}
{"id":"910005","source":"W3_8_QUALITY","externalId":"deadline-later-backend","canonicalFingerprint":"w3-8-deadline-later-backend","title":"백엔드 플랫폼 개발자","companyName":"Example Deadline B","description":"Spring Boot 백엔드 플랫폼 API 개발을 담당합니다.","role":"BACKEND","roleDetail":"Java Spring","careerLevel":"ANY","employmentType":"FULL_TIME","industry":"IT","locationCountry":"KR","locationRegion":"Seoul","locationCity":"Yeongdeungpo","remoteType":"ONSITE","deadlineAt":"2026-07-31T23:59:00","createdAt":"2026-06-05T00:00:00","updatedAt":"2026-06-05T00:00:00"}
NDJSON

search() {
  local keyword="$1"

  echo
  echo "### ${keyword}"

  curl -sS -X POST "${ES_URL}/${INDEX_NAME}/_search" \
    -H "Content-Type: application/json" \
    -d @- <<JSON | jq -r '.hits.hits[] | "\(.["_source"].externalId)\t\(.["_source"].title)\tdeadline=\(.["_source"].deadlineAt)\tscore=\(._score)"'
{
  "size": 10,
  "query": {
    "bool": {
      "filter": [
        {
          "term": {
            "source": "W3_8_QUALITY"
          }
        }
      ],
      "must": [
        {
          "multi_match": {
            "query": "${keyword}",
            "fields": [
              "title^4",
              "roleDetail^3",
              "description^2",
              "companyName",
              "industry",
              "locationRegion",
              "locationCity"
            ],
            "type": "best_fields",
            "operator": "or"
          }
        }
      ]
    }
  },
  "sort": [
    {
      "_score": "desc"
    },
    {
      "createdAt": "desc"
    }
  ]
}
JSON
}

search "k8s"
search "Kubernetes"
search "백엔드 플랫폼"
search "Spring"
