#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "${script_dir}/../../.." && pwd)"
compose_file="${repository_root}/docker/infra-compose.yml"
compose_project="${PERF_COMPOSE_PROJECT:-docker}"
fixture_count="${FIXTURE_COUNT:-100}"
target_date="${TARGET_DATE:-}"
database_name="${PERF_API_DATABASE_NAME:-loopers}"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

validate_date() {
  local value="$1"
  if [[ ! "${value}" =~ ^[0-9]{8}$ ]]; then
    fail "TARGET_DATE must use yyyyMMdd."
  fi

  local year=$((10#${value:0:4}))
  local month=$((10#${value:4:2}))
  local day=$((10#${value:6:2}))
  local max_day

  case "${month}" in
    1 | 3 | 5 | 7 | 8 | 10 | 12) max_day=31 ;;
    4 | 6 | 9 | 11) max_day=30 ;;
    2)
      max_day=28
      if ((year % 400 == 0 || (year % 4 == 0 && year % 100 != 0))); then
        max_day=29
      fi
      ;;
    *) fail "TARGET_DATE is not a valid calendar date." ;;
  esac

  if ((day < 1 || day > max_day)); then
    fail "TARGET_DATE is not a valid calendar date."
  fi
}

if [[ "${PERF_CONFIRM_API_FIXTURE:-}" != "YES" ]]; then
  fail "Set PERF_CONFIRM_API_FIXTURE=YES to replace only the requested date's local ranking fixture."
fi

if [[ ! "${database_name}" =~ ^[A-Za-z0-9_]+$ ]]; then
  fail "PERF_API_DATABASE_NAME may contain only letters, numbers, and underscores."
fi
if [[ "${database_name}" != "loopers" && ! "${database_name}" =~ _perf$ ]]; then
  fail "A non-default PERF_API_DATABASE_NAME must end with _perf."
fi

validate_date "${target_date}"

if [[ ! "${fixture_count}" =~ ^[1-9][0-9]*$ ]] || ((fixture_count > 100)); then
  fail "FIXTURE_COUNT must be an integer between 1 and 100."
fi

command -v docker >/dev/null 2>&1 || fail "docker is required."
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is required."
[[ -f "${compose_file}" ]] || fail "Local Compose file not found: ${compose_file}"

compose=(docker compose --project-name "${compose_project}" --file "${compose_file}")

mysql_client() {
  "${compose[@]}" exec -T \
    --env "PERF_API_DATABASE_NAME=${database_name}" \
    mysql sh -c \
    'MYSQL_PWD="${MYSQL_PASSWORD:?MYSQL_PASSWORD is not set}" exec mysql --protocol=TCP --host=127.0.0.1 --user="${MYSQL_USER:?MYSQL_USER is not set}" --database="${PERF_API_DATABASE_NAME:?PERF_API_DATABASE_NAME is not set}" --batch --skip-column-names --raw --silent'
}

required_mv_count="$({
  printf '%s\n' \
    "SELECT COUNT(*)" \
    "FROM information_schema.tables" \
    "WHERE table_schema = DATABASE()" \
    "  AND table_name IN ('mv_product_rank_weekly', 'mv_product_rank_monthly');"
} | mysql_client)" || fail "Unable to inspect MV tables in local MySQL."

if [[ "${required_mv_count}" != "2" ]]; then
  fail "Required MV tables are missing in ${database_name}. Apply database/schema/product-ranking-aggregation.sql first."
fi

redis_ping="$("${compose[@]}" exec -T redis-master redis-cli --raw PING)" \
  || fail "Unable to connect to local redis-master."
[[ "${redis_ping}" == "PONG" ]] || fail "Local redis-master did not return PONG."

product_output="$(printf \
  'SELECT id FROM product WHERE deleted_at IS NULL ORDER BY id LIMIT %d;\n' \
  "${fixture_count}" | mysql_client)" \
  || fail "Unable to query active products from ${database_name}.product."

product_ids=()
while IFS= read -r product_id; do
  [[ -n "${product_id}" ]] || continue
  [[ "${product_id}" =~ ^[1-9][0-9]*$ ]] \
    || fail "MySQL returned an invalid product ID; no fixture was changed."
  product_ids+=("${product_id}")
done <<< "${product_output}"

if ((${#product_ids[@]} != fixture_count)); then
  fail "Requested ${fixture_count} active products, but found ${#product_ids[@]}. Lower FIXTURE_COUNT or seed products first."
fi

weekly_values=""
monthly_values=""
redis_arguments=()

for index in "${!product_ids[@]}"; do
  rank=$((index + 1))
  score=$((fixture_count - index))
  product_id="${product_ids[${index}]}"
  separator=","
  if ((index == fixture_count - 1)); then
    separator=";"
  fi

  weekly_values+="(@target_date, DATE_SUB(@target_date, INTERVAL WEEKDAY(@target_date) DAY), @target_date, ${rank}, ${product_id}, ${score}.0, CURRENT_TIMESTAMP(6))${separator}"
  monthly_values+="(@target_date, CAST(DATE_FORMAT(@target_date, '%Y-%m-01') AS DATE), @target_date, ${rank}, ${product_id}, ${score}.0, CURRENT_TIMESTAMP(6))${separator}"
  redis_arguments+=("${score}.0" "${product_id}")
done

snapshot_sql="
SET @target_date = STR_TO_DATE('${target_date}', '%Y%m%d');
START TRANSACTION;
DELETE FROM mv_product_rank_weekly WHERE aggregation_date = @target_date;
DELETE FROM mv_product_rank_monthly WHERE aggregation_date = @target_date;
INSERT INTO mv_product_rank_weekly
  (aggregation_date, period_start_date, period_end_date, rank_position, product_id, score, generated_at)
VALUES ${weekly_values}
INSERT INTO mv_product_rank_monthly
  (aggregation_date, period_start_date, period_end_date, rank_position, product_id, score, generated_at)
VALUES ${monthly_values}
COMMIT;
"

printf '%s\n' "${snapshot_sql}" | mysql_client >/dev/null \
  || fail "Failed to replace local weekly/monthly snapshots; the MySQL transaction was not committed."

daily_key="ranking:all:${target_date}"
redis_result="$("${compose[@]}" exec -T redis-master redis-cli --raw EVAL \
  'redis.call("DEL", KEYS[1]); return redis.call("ZADD", KEYS[1], unpack(ARGV))' \
  1 "${daily_key}" "${redis_arguments[@]}")" \
  || fail "Failed to replace the local Redis daily ranking key. Re-run this deterministic fixture command."

if [[ "${redis_result}" != "${fixture_count}" ]]; then
  fail "Redis daily fixture count mismatch: expected ${fixture_count}, got ${redis_result}."
fi

snapshot_counts="$(printf \
  "SELECT (SELECT COUNT(*) FROM mv_product_rank_weekly WHERE aggregation_date = STR_TO_DATE('%s', '%%Y%%m%%d')), (SELECT COUNT(*) FROM mv_product_rank_monthly WHERE aggregation_date = STR_TO_DATE('%s', '%%Y%%m%%d'));\n" \
  "${target_date}" "${target_date}" | mysql_client)" \
  || fail "Unable to verify local MV fixture counts."
redis_count="$("${compose[@]}" exec -T redis-master redis-cli --raw ZCARD "${daily_key}")" \
  || fail "Unable to verify the local Redis daily fixture count."

expected_counts="${fixture_count}"$'\t'"${fixture_count}"
[[ "${snapshot_counts}" == "${expected_counts}" ]] \
  || fail "MV fixture verification failed: expected ${fixture_count} rows in each target snapshot."
[[ "${redis_count}" == "${fixture_count}" ]] \
  || fail "Redis fixture verification failed: expected ${fixture_count} members."

echo "Ranking API fixture prepared for local Compose only."
echo "database=${database_name} targetDate=${target_date} products=${fixture_count}"
echo "replaced=mv_product_rank_weekly, mv_product_rank_monthly, ${daily_key}"
