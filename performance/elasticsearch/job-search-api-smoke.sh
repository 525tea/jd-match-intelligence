#!/usr/bin/env bash

set -euo pipefail

ES_URL="${ES_URL:-http://localhost:9200}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
INDEX_NAME="${INDEX_NAME:-jobflow-jobs}"

if ! curl -fsS "${ES_URL}/${INDEX_NAME}" >/dev/null; then
  echo "Elasticsearch index '${INDEX_NAME}' does not exist."
  echo "Start backend with search index initialization first, or create the index before running this smoke test."
  exit 1
fi

curl -sS -X POST "${ES_URL}/${INDEX_NAME}/_delete_by_query?conflicts=proceed" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "term": {
        "source": "W3_4_SMOKE"
      }
    }
  }' >/dev/null

curl -sS -X POST "${ES_URL}/${INDEX_NAME}/_bulk?refresh=true" \
  -H "Content-Type: application/x-ndjson" \
  --data-binary @- >/dev/null <<'NDJSON'
{"index":{"_id":"900001"}}
{"id":"900001","source":"W3_4_SMOKE","externalId":"urgent-backend","canonicalFingerprint":"w3-4-urgent-backend","title":"백엔드 개발자","companyName":"JobFlow","description":"Spring Boot 기반 백엔드 API 개발","role":"BACKEND","roleDetail":"Java Spring Boot JPA","careerLevel":"JUNIOR","employmentType":"FULL_TIME","industry":"IT","locationCountry":"KR","locationRegion":"Seoul","locationCity":"Gangnam","remoteType":"HYBRID","deadlineAt":"2026-06-07T23:59:00","createdAt":"2026-06-05T09:00:00","updatedAt":"2026-06-05T09:00:00"}
{"index":{"_id":"900002"}}
{"id":"900002","source":"W3_4_SMOKE","externalId":"later-backend","canonicalFingerprint":"w3-4-later-backend","title":"백엔드 개발자","companyName":"JobFlow","description":"Spring Boot 기반 백엔드 API 개발","role":"BACKEND","roleDetail":"Java Spring Boot JPA","careerLevel":"JUNIOR","employmentType":"FULL_TIME","industry":"IT","locationCountry":"KR","locationRegion":"Seoul","locationCity":"Gangnam","remoteType":"HYBRID","deadlineAt":"2026-07-31T23:59:00","createdAt":"2026-06-01T09:00:00","updatedAt":"2026-06-01T09:00:00"}
{"index":{"_id":"900003"}}
{"id":"900003","source":"W3_4_SMOKE","externalId":"kubernetes-platform","canonicalFingerprint":"w3-4-kubernetes-platform","title":"Kubernetes 플랫폼 엔지니어","companyName":"JobFlow","description":"k8s 기반 플랫폼 운영과 Spring 서비스 배포","role":"BACKEND","roleDetail":"Kubernetes k8s platform","careerLevel":"MID","employmentType":"FULL_TIME","industry":"IT","locationCountry":"KR","locationRegion":"Seoul","locationCity":"Seongdong","remoteType":"REMOTE","deadlineAt":"2026-06-20T23:59:00","createdAt":"2026-06-05T09:00:00","updatedAt":"2026-06-05T09:00:00"}
NDJSON

search() {
  local keyword="$1"

  echo
  echo "### ${keyword}"
  curl -sS -G "${BASE_URL}/jobs/search" \
    --data-urlencode "keyword=${keyword}" \
    --data-urlencode "limit=10" \
    | jq -r '.data[] | "\(.id)\t\(.title)\tdeadline=\(.deadlineAt)\tscore=\(.score)"'
}

search "백엔드"
search "k8s"
search "Kubernetes"
