#!/usr/bin/env bash

set -euo pipefail

PROFILE="${PROFILE:-local}"
ES_INDEX_ALIAS="${ES_INDEX_ALIAS:-jobflow-jobs}"
ES_PHYSICAL_INDEX="${ES_PHYSICAL_INDEX:-jobflow-jobs-v1}"
REINDEX_BATCH_SIZE="${REINDEX_BATCH_SIZE:-100}"

echo "PROFILE=${PROFILE}"
echo "ES_INDEX_ALIAS=${ES_INDEX_ALIAS}"
echo "ES_PHYSICAL_INDEX=${ES_PHYSICAL_INDEX}"
echo "REINDEX_BATCH_SIZE=${REINDEX_BATCH_SIZE}"
echo "NOTE: This command creates the physical Elasticsearch index, attaches the alias, and reindexes jobs from MySQL."

cd "$(dirname "$0")/../../backend"

./gradlew bootRun \
  --args="--spring.profiles.active=${PROFILE} \
--spring.main.web-application-type=none \
--app.search.elasticsearch.initialize-on-startup=true \
--app.search.elasticsearch.reindex-on-startup=true \
--app.search.elasticsearch.index-name=${ES_INDEX_ALIAS} \
--app.search.elasticsearch.physical-index-name=${ES_PHYSICAL_INDEX} \
--app.search.elasticsearch.reindex-batch-size=${REINDEX_BATCH_SIZE}"
