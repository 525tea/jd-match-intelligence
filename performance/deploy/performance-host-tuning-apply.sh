#!/usr/bin/env bash
set -euo pipefail

APPLY="${APPLY:-false}"
PERF_SYSCTL_SOMAXCONN="${PERF_SYSCTL_SOMAXCONN:-65535}"
PERF_SYSCTL_TCP_MAX_SYN_BACKLOG="${PERF_SYSCTL_TCP_MAX_SYN_BACKLOG:-65535}"
PERF_SYSCTL_NETDEV_MAX_BACKLOG="${PERF_SYSCTL_NETDEV_MAX_BACKLOG:-65535}"
PERF_SYSCTL_IP_LOCAL_PORT_RANGE="${PERF_SYSCTL_IP_LOCAL_PORT_RANGE:-1024 65535}"
PERF_SYSCTL_FS_FILE_MAX="${PERF_SYSCTL_FS_FILE_MAX:-1000000}"

commands=(
  "sudo sysctl -w net.core.somaxconn=${PERF_SYSCTL_SOMAXCONN}"
  "sudo sysctl -w net.ipv4.tcp_max_syn_backlog=${PERF_SYSCTL_TCP_MAX_SYN_BACKLOG}"
  "sudo sysctl -w net.core.netdev_max_backlog=${PERF_SYSCTL_NETDEV_MAX_BACKLOG}"
  "sudo sysctl -w net.ipv4.ip_local_port_range='${PERF_SYSCTL_IP_LOCAL_PORT_RANGE}'"
  "sudo sysctl -w fs.file-max=${PERF_SYSCTL_FS_FILE_MAX}"
)

echo "APPLY=${APPLY}"
echo "PERF_SYSCTL_SOMAXCONN=${PERF_SYSCTL_SOMAXCONN}"
echo "PERF_SYSCTL_TCP_MAX_SYN_BACKLOG=${PERF_SYSCTL_TCP_MAX_SYN_BACKLOG}"
echo "PERF_SYSCTL_NETDEV_MAX_BACKLOG=${PERF_SYSCTL_NETDEV_MAX_BACKLOG}"
echo "PERF_SYSCTL_IP_LOCAL_PORT_RANGE=${PERF_SYSCTL_IP_LOCAL_PORT_RANGE}"
echo "PERF_SYSCTL_FS_FILE_MAX=${PERF_SYSCTL_FS_FILE_MAX}"
echo

if [[ "${APPLY}" != "true" ]]; then
  echo "dry_run=true"
  printf '%s\n' "${commands[@]}"
  echo
  echo "Rerun with APPLY=true to apply these sysctl values on the host."
  exit 0
fi

echo "dry_run=false"
for command in "${commands[@]}"; do
  echo "+ ${command}"
  eval "${command}"
done

echo
echo "host_tuning_apply=ok"
