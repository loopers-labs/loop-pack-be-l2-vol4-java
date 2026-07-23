#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "${script_dir}/../../.." && pwd)"

if ! command -v k6 >/dev/null 2>&1; then
  echo "k6 is required: https://grafana.com/docs/k6/latest/set-up/install-k6/" >&2
  exit 127
fi

if [[ -z "${TARGET_DATE:-}" ]]; then
  echo "TARGET_DATE is required and must use yyyyMMdd." >&2
  echo "Example: TARGET_DATE=20260722 ${0}" >&2
  exit 2
fi

summary_path="${SUMMARY_PATH:-${repository_root}/build/reports/k6/ranking-api-summary.json}"
mkdir -p "$(dirname "${summary_path}")"

SUMMARY_PATH="${summary_path}" k6 run "$@" "${script_dir}/ranking-api.js"
