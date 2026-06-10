#!/usr/bin/env bash

set -euo pipefail

PROFILE="${PROFILE:-local}"
ES_URL="${ES_URL:-http://localhost:9200}"
ES_INDEX_ALIAS="${ES_INDEX_ALIAS:-jobflow-jobs}"
ES_PHYSICAL_INDEX="${ES_PHYSICAL_INDEX:-jobflow-jobs-v1}"
REINDEX_BATCH_SIZE="${REINDEX_BATCH_SIZE:-100}"
DELETE_LEGACY_ALIAS_INDEX="${DELETE_LEGACY_ALIAS_INDEX:-false}"

echo "PROFILE=${PROFILE}"
echo "ES_URL=${ES_URL}"
echo "ES_INDEX_ALIAS=${ES_INDEX_ALIAS}"
echo "ES_PHYSICAL_INDEX=${ES_PHYSICAL_INDEX}"
echo "REINDEX_BATCH_SIZE=${REINDEX_BATCH_SIZE}"
echo "DELETE_LEGACY_ALIAS_INDEX=${DELETE_LEGACY_ALIAS_INDEX}"
echo "NOTE: This command creates the physical Elasticsearch index, attaches the alias, and reindexes jobs from MySQL."

if curl -fsS "${ES_URL}/_alias/${ES_INDEX_ALIAS}" >/dev/null 2>&1; then
  echo "Alias '${ES_INDEX_ALIAS}' already exists."
elif curl -fsS "${ES_URL}/${ES_INDEX_ALIAS}" >/dev/null 2>&1; then
  echo "Legacy Elasticsearch index '${ES_INDEX_ALIAS}' exists with the same name as the target alias."

  if [[ "${DELETE_LEGACY_ALIAS_INDEX}" != "true" ]]; then
    echo "Refusing to delete it automatically."
    echo "Re-run with DELETE_LEGACY_ALIAS_INDEX=true when you want to replace the legacy index with alias-based indexing."
    echo
    echo "Example:"
    echo "DELETE_LEGACY_ALIAS_INDEX=true bash performance/elasticsearch/reindex-real-jobs.sh"
    exit 1
  fi

  echo "Deleting legacy Elasticsearch index '${ES_INDEX_ALIAS}' before alias creation..."
  curl -fsS -X DELETE "${ES_URL}/${ES_INDEX_ALIAS}" >/dev/null
else
  echo "Alias '${ES_INDEX_ALIAS}' is available."
fi

cd "$(dirname "$0")/../../backend"

./gradlew bootRun \
  --args="--spring.profiles.active=${PROFILE} \
--spring.main.web-application-type=none \
--app.search.elasticsearch.initialize-on-startup=true \
--app.search.elasticsearch.reindex-on-startup=true \
--app.search.elasticsearch.index-name=${ES_INDEX_ALIAS} \
--app.search.elasticsearch.physical-index-name=${ES_PHYSICAL_INDEX} \
--app.search.elasticsearch.reindex-batch-size=${REINDEX_BATCH_SIZE}"
