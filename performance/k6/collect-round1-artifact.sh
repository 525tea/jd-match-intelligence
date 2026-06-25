#!/usr/bin/env bash
set -euo pipefail

SSH_HOST="${SSH_HOST:-}"
SSH_KEY="${SSH_KEY:-/Users/iyejin/dev/jobflow-server-env/jobflow-staging-key.pem}"
REMOTE_SUMMARY="${REMOTE_SUMMARY:-/tmp/jobflow-k6-round1-baseline-summary.json}"
LOCAL_ARTIFACT_DIR="${LOCAL_ARTIFACT_DIR:-/Users/iyejin/dev/jobflow-server-env/artifacts/260625_k6_round1}"
ARTIFACT_NAME="${ARTIFACT_NAME:-k6_round1_${VUS:-unknown}vu_${DURATION:-unknown}_summary.json}"

fail() {
  echo "Assertion failed: $*" >&2
  exit 1
}

[[ -n "${SSH_HOST}" ]] || fail "SSH_HOST is required. Example: SSH_HOST=ubuntu@3.38.220.29"
[[ -f "${SSH_KEY}" ]] || fail "SSH_KEY does not exist: ${SSH_KEY}"

mkdir -p "${LOCAL_ARTIFACT_DIR}"

echo "SSH_HOST=${SSH_HOST}"
echo "SSH_KEY=${SSH_KEY}"
echo "REMOTE_SUMMARY=${REMOTE_SUMMARY}"
echo "LOCAL_ARTIFACT_DIR=${LOCAL_ARTIFACT_DIR}"
echo "ARTIFACT_NAME=${ARTIFACT_NAME}"
echo

scp -i "${SSH_KEY}" \
  "${SSH_HOST}:${REMOTE_SUMMARY}" \
  "${LOCAL_ARTIFACT_DIR}/${ARTIFACT_NAME}"

echo
echo "Round 1 k6 summary artifact collected."
echo "local_artifact=${LOCAL_ARTIFACT_DIR}/${ARTIFACT_NAME}"
