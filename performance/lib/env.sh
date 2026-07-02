#!/usr/bin/env bash

load_env_file_preserving_existing() {
  local env_file="$1"
  local key=""
  local line=""
  local value=""

  [[ -f "${env_file}" ]] || return 0

  while IFS= read -r line || [[ -n "${line}" ]]; do
    [[ "${line}" =~ ^[[:space:]]*# ]] && continue
    [[ -z "${line// }" ]] && continue
    key="${line%%=*}"
    value="${line#*=}"

    key="${key#"${key%%[![:space:]]*}"}"
    key="${key%"${key##*[![:space:]]}"}"
    [[ -z "${key}" ]] && continue
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    declare -p "${key}" >/dev/null 2>&1 && continue

    value="${value%\"}" value="${value#\"}"
    value="${value%\'}" value="${value#\'}"
    export "${key}=${value}"
  done < "${env_file}"
}
