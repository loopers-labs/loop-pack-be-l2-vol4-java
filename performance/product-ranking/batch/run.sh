#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIR}/../../.." && pwd)"
SQL_DIR="${SCRIPT_DIR}/sql"

TARGET_DATE="${PERF_TARGET_DATE:-}"
DAILY_PRODUCTS="${PERF_DAILY_PRODUCTS:-100000}"
DAYS="${PERF_DAYS:-31}"
DATA_SEED="${PERF_DATA_SEED:-42}"
REBUILD_SEQUENCE="${PERF_REBUILD_SEQUENCE:-$(date -u '+%Y%m%d%H%M%S')}"
PRODUCT_ID_BASE="${PERF_PRODUCT_ID_BASE:-8000000000000000}"
MAX_DAILY_PRODUCTS=1000000
BUILD_IMAGE="${PERF_BUILD_IMAGE:-true}"
START_INFRA="${PERF_START_INFRA:-true}"
SAMPLE_INTERVAL_SECONDS="${PERF_SAMPLE_INTERVAL_SECONDS:-5}"
CHUNK_SIZE="${PERF_CHUNK_SIZE:-100}"
CHUNK_LOG_INTERVAL="${PERF_LOG_INTERVAL:-100}"
CONTAINER_CPUS="${PERF_CPUS:-2.0}"
CONTAINER_MEMORY="${PERF_MEMORY:-2g}"
COMPOSE_PROJECT_NAME="${PERF_COMPOSE_PROJECT_NAME:-docker}"
PERFORMANCE_DATABASE="${PERF_DATABASE_NAME:-loopers_product_ranking_perf}"
RESET_DATABASE="${PERF_RESET_DATABASE:-true}"
PREPARE_DATASET="${PERF_PREPARE_DATASET:-true}"
BATCH_IMAGE="${BATCH_IMAGE:-loopers/commerce-batch:perf}"
MYSQL_USER="${MYSQL_USER:-application}"
MYSQL_PWD="${MYSQL_PWD:-application}"

CONTAINER_ID=""

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "필수 명령을 찾을 수 없습니다: $1"
}

validate_positive_integer() {
  local name="$1"
  local value="$2"

  [[ "${value}" =~ ^[1-9][0-9]*$ ]] || fail "${name}은(는) 1 이상의 정수여야 합니다."
}

validate_non_negative_integer() {
  local name="$1"
  local value="$2"

  [[ "${value}" =~ ^[0-9]+$ ]] || fail "${name}은(는) 0 이상의 정수여야 합니다."
}

validate_boolean() {
  local name="$1"
  local value="$2"

  [[ "${value}" == "true" || "${value}" == "false" ]] \
    || fail "${name}은(는) true 또는 false여야 합니다."
}

cleanup() {
  if [[ -n "${CONTAINER_ID}" ]] && docker container inspect "${CONTAINER_ID}" >/dev/null 2>&1; then
    docker container rm --force "${CONTAINER_ID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

require_command docker
require_command awk
require_command git

[[ "${PERFORMANCE_DATABASE}" =~ ^[A-Za-z0-9_]+_perf$ ]] \
  || fail "PERF_DATABASE_NAME은 영문/숫자/밑줄로 구성되고 _perf로 끝나야 합니다."
[[ "${PERF_CONFIRM_DATABASE_RESET:-}" == "${PERFORMANCE_DATABASE}" ]] \
  || fail "PERF_CONFIRM_DATABASE_RESET에 테스트 DB 이름(${PERFORMANCE_DATABASE})을 그대로 입력하세요."
[[ "${MYSQL_USER}" =~ ^[A-Za-z0-9_]+$ ]] \
  || fail "MYSQL_USER는 영문/숫자/밑줄만 허용합니다."
[[ "${TARGET_DATE}" =~ ^[0-9]{8}$ ]] || fail "PERF_TARGET_DATE는 yyyyMMdd 형식이어야 합니다."

validate_positive_integer PERF_DAILY_PRODUCTS "${DAILY_PRODUCTS}"
validate_positive_integer PERF_DAYS "${DAYS}"
validate_positive_integer PERF_DATA_SEED "${DATA_SEED}"
validate_positive_integer PERF_REBUILD_SEQUENCE "${REBUILD_SEQUENCE}"
validate_positive_integer PERF_SAMPLE_INTERVAL_SECONDS "${SAMPLE_INTERVAL_SECONDS}"
validate_positive_integer PERF_CHUNK_SIZE "${CHUNK_SIZE}"
validate_non_negative_integer PERF_LOG_INTERVAL "${CHUNK_LOG_INTERVAL}"
validate_non_negative_integer PERF_PRODUCT_ID_BASE "${PRODUCT_ID_BASE}"
validate_boolean PERF_BUILD_IMAGE "${BUILD_IMAGE}"
validate_boolean PERF_START_INFRA "${START_INFRA}"
validate_boolean PERF_RESET_DATABASE "${RESET_DATABASE}"
validate_boolean PERF_PREPARE_DATASET "${PREPARE_DATASET}"

if [[ "${RESET_DATABASE}" == "true" && "${PREPARE_DATASET}" == "false" ]]; then
  fail "PERF_RESET_DATABASE=true이면 PERF_PREPARE_DATASET=false를 사용할 수 없습니다."
fi

(( DAILY_PRODUCTS <= MAX_DAILY_PRODUCTS )) \
  || fail "PERF_DAILY_PRODUCTS의 최대값은 ${MAX_DAILY_PRODUCTS}입니다."
(( DAYS <= 31 )) || fail "PERF_DAYS의 최대값은 31입니다."
(( PRODUCT_ID_BASE <= 9223372036854775807 - MAX_DAILY_PRODUCTS )) \
  || fail "PERF_PRODUCT_ID_BASE와 최대 상품 수의 합은 signed BIGINT 범위여야 합니다."
[[ "${CONTAINER_CPUS}" =~ ^[0-9]+([.][0-9]+)?$ \
  && ! "${CONTAINER_CPUS}" =~ ^0+([.]0+)?$ ]] \
  || fail "PERF_CPUS는 0보다 큰 CPU 수여야 합니다. 예: 2.0"
[[ "${CONTAINER_MEMORY}" =~ ^[1-9][0-9]*([bBkKmMgG]|[kKmMgG][bB])$ ]] \
  || fail "PERF_MEMORY는 단위가 있는 메모리 크기여야 합니다. 예: 2g"

cd "${REPOSITORY_ROOT}"

docker version >/dev/null

RUN_KEY="${TARGET_DATE}-${REBUILD_SEQUENCE}"
ATTEMPT_ID="$(date -u '+%Y%m%d%H%M%S')-$$"
OUTPUT_DIR="${PERF_OUTPUT_DIR:-${REPOSITORY_ROOT}/build/performance/product-ranking/${RUN_KEY}-${ATTEMPT_ID}}"
[[ ! -e "${OUTPUT_DIR}" ]] || fail "결과 디렉터리가 이미 존재합니다: ${OUTPUT_DIR}"
mkdir -p "${OUTPUT_DIR}"

COMPOSE_INFRA=(
  docker compose
  --project-name "${COMPOSE_PROJECT_NAME}"
  --file docker/infra-compose.yml
)
COMPOSE_BATCH=(
  docker compose
  --project-name "${COMPOSE_PROJECT_NAME}"
  --file docker/infra-compose.yml
  --file docker/batch-compose.yml
  --file "${SCRIPT_DIR}/batch-performance-compose.yml"
  --profile batch
)

if [[ "${START_INFRA}" == "true" ]]; then
  "${COMPOSE_INFRA[@]}" up --detach mysql
fi

db_server_query_value() {
  local query="$1"

  "${COMPOSE_INFRA[@]}" exec --no-TTY mysql sh -eu -c '
    export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"
    exec mysql \
      --protocol=socket \
      --user=root \
      --batch \
      --raw \
      --skip-column-names \
      --execute="$1"
  ' sh "${query}"
}

db_query_value() {
  local query="$1"

  "${COMPOSE_INFRA[@]}" exec \
    --no-TTY \
    --env "PERF_DATABASE_NAME=${PERFORMANCE_DATABASE}" \
    mysql \
    sh -eu -c '
    export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"
    exec mysql \
      --protocol=socket \
      --user=root \
      --database="${PERF_DATABASE_NAME}" \
      --batch \
      --raw \
      --skip-column-names \
      --execute="$1"
  ' sh "${query}"
}

db_query_report() {
  local query="$1"

  "${COMPOSE_INFRA[@]}" exec \
    --no-TTY \
    --env "PERF_DATABASE_NAME=${PERFORMANCE_DATABASE}" \
    mysql \
    sh -eu -c '
    export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"
    exec mysql \
      --protocol=socket \
      --user=root \
      --database="${PERF_DATABASE_NAME}" \
      --batch \
      --raw \
      --execute="$1"
  ' sh "${query}"
}

db_execute_file() {
  local file="$1"
  local init_command="${2:-}"

  "${COMPOSE_INFRA[@]}" exec \
    --no-TTY \
    --env "PERF_DATABASE_NAME=${PERFORMANCE_DATABASE}" \
    --env "PERF_MYSQL_INIT=${init_command}" \
    mysql \
    sh -eu -c '
      export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"
      if [ -n "${PERF_MYSQL_INIT}" ]; then
        exec mysql \
          --protocol=socket \
          --user=root \
          --database="${PERF_DATABASE_NAME}" \
          --batch \
          --raw \
          --init-command="${PERF_MYSQL_INIT}"
      fi
      exec mysql \
        --protocol=socket \
        --user=root \
        --database="${PERF_DATABASE_NAME}" \
        --batch \
        --raw
    ' < "${file}"
}

db_execute_file_no_headers() {
  local file="$1"
  local init_command="${2:-}"

  "${COMPOSE_INFRA[@]}" exec \
    --no-TTY \
    --env "PERF_DATABASE_NAME=${PERFORMANCE_DATABASE}" \
    --env "PERF_MYSQL_INIT=${init_command}" \
    mysql \
    sh -eu -c '
      export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"
      exec mysql \
        --protocol=socket \
        --user=root \
        --database="${PERF_DATABASE_NAME}" \
        --batch \
        --raw \
        --skip-column-names \
        --init-command="${PERF_MYSQL_INIT}"
    ' < "${file}"
}

mysql_ready=false
for _ in {1..60}; do
  if db_server_query_value 'SELECT 1' >/dev/null 2>&1; then
    mysql_ready=true
    break
  fi
  sleep 2
done
[[ "${mysql_ready}" == "true" ]] || fail "MySQL이 120초 안에 준비되지 않았습니다."

valid_target_date="$(db_server_query_value "
  SELECT CASE
    WHEN STR_TO_DATE('${TARGET_DATE}', '%Y%m%d') IS NOT NULL
     AND DATE_FORMAT(STR_TO_DATE('${TARGET_DATE}', '%Y%m%d'), '%Y%m%d') = '${TARGET_DATE}'
     AND DAY(STR_TO_DATE('${TARGET_DATE}', '%Y%m%d')) <= DAY(
       LAST_DAY(STR_TO_DATE(CONCAT(LEFT('${TARGET_DATE}', 6), '01'), '%Y%m%d'))
     )
    THEN 1
    ELSE 0
  END
")"
[[ "${valid_target_date}" == "1" ]] || fail "PERF_TARGET_DATE가 유효한 날짜가 아닙니다: ${TARGET_DATE}"

if [[ "${RESET_DATABASE}" == "true" ]]; then
  db_server_query_value "
    DROP DATABASE IF EXISTS \`${PERFORMANCE_DATABASE}\`;
    CREATE DATABASE \`${PERFORMANCE_DATABASE}\`
      CHARACTER SET utf8mb4
      COLLATE utf8mb4_general_ci;
  " >/dev/null
else
  database_exists="$(db_server_query_value "
    SELECT COUNT(*)
    FROM information_schema.SCHEMATA
    WHERE SCHEMA_NAME = '${PERFORMANCE_DATABASE}'
  ")"
  [[ "${database_exists}" == "1" ]] \
    || fail "PERF_RESET_DATABASE=false지만 테스트 DB가 존재하지 않습니다: ${PERFORMANCE_DATABASE}"
fi

db_server_query_value "
  GRANT ALL PRIVILEGES ON \`${PERFORMANCE_DATABASE}\`.* TO '${MYSQL_USER}'@'%'
" >/dev/null

ensure_schema_group() {
  local label="$1"
  local expected_count="$2"
  local table_names="$3"
  local ddl_file="$4"
  local table_count

  table_count="$(db_query_value "
    SELECT COUNT(*)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND UPPER(TABLE_NAME) IN (${table_names})
  ")"

  if [[ "${table_count}" == "0" ]]; then
    printf '%s 스키마를 적용합니다.\n' "${label}"
    db_execute_file "${ddl_file}" >/dev/null
  elif [[ "${table_count}" != "${expected_count}" ]]; then
    fail "${label} 스키마가 일부만 존재합니다(${table_count}/${expected_count}). DDL 상태를 먼저 복구하세요."
  fi
}

ensure_schema_group \
  'Spring Batch metadata' \
  9 \
  "'BATCH_JOB_INSTANCE','BATCH_JOB_EXECUTION','BATCH_JOB_EXECUTION_PARAMS','BATCH_STEP_EXECUTION','BATCH_STEP_EXECUTION_CONTEXT','BATCH_JOB_EXECUTION_CONTEXT','BATCH_STEP_EXECUTION_SEQ','BATCH_JOB_EXECUTION_SEQ','BATCH_JOB_SEQ'" \
  "${REPOSITORY_ROOT}/database/schema/spring-batch-mysql.sql"

ensure_schema_group \
  'Product ranking MV/staging' \
  3 \
  "'MV_PRODUCT_RANK_WEEKLY','MV_PRODUCT_RANK_MONTHLY','STG_PRODUCT_RANK_AGGREGATION'" \
  "${REPOSITORY_ROOT}/database/schema/product-ranking-aggregation.sql"

git_revision="$(git rev-parse HEAD)"
git_dirty=false
if [[ -n "$(git status --porcelain)" ]]; then
  git_dirty=true
fi

{
  printf 'target_date=%s\n' "${TARGET_DATE}"
  printf 'daily_products=%s\n' "${DAILY_PRODUCTS}"
  printf 'days=%s\n' "${DAYS}"
  printf 'data_seed=%s\n' "${DATA_SEED}"
  printf 'product_id_base=%s\n' "${PRODUCT_ID_BASE}"
  printf 'database_name=%s\n' "${PERFORMANCE_DATABASE}"
  printf 'database_reset=%s\n' "${RESET_DATABASE}"
  printf 'prepare_dataset=%s\n' "${PREPARE_DATASET}"
  printf 'rebuild_sequence=%s\n' "${REBUILD_SEQUENCE}"
  printf 'batch_image=%s\n' "${BATCH_IMAGE}"
  printf 'sample_interval_seconds=%s\n' "${SAMPLE_INTERVAL_SECONDS}"
  printf 'chunk_size=%s\n' "${CHUNK_SIZE}"
  printf 'chunk_log_interval=%s\n' "${CHUNK_LOG_INTERVAL}"
  printf 'container_cpus=%s\n' "${CONTAINER_CPUS}"
  printf 'container_memory=%s\n' "${CONTAINER_MEMORY}"
  printf 'git_revision=%s\n' "${git_revision}"
  printf 'git_dirty=%s\n' "${git_dirty}"
  printf 'started_at_utc=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
} > "${OUTPUT_DIR}/manifest.env"

docker_environment="$(docker info --format '{{.ServerVersion}}|{{.NCPU}}|{{.MemTotal}}')"
IFS='|' read -r docker_server_version docker_cpu_count docker_memory_bytes \
  <<< "${docker_environment}"
{
  printf 'server_version\t%s\n' "${docker_server_version}"
  printf 'cpu_count\t%s\n' "${docker_cpu_count}"
  printf 'memory_bytes\t%s\n' "${docker_memory_bytes}"
} > "${OUTPUT_DIR}/docker-environment.tsv"

seed_elapsed_seconds=0
if [[ "${PREPARE_DATASET}" == "true" ]]; then
  prepare_init="SET @target_date = STR_TO_DATE('${TARGET_DATE}', '%Y%m%d'), @product_id_base = ${PRODUCT_ID_BASE}, @max_daily_products = ${MAX_DAILY_PRODUCTS};"
  db_execute_file "${SQL_DIR}/prepare-dataset.sql" "${prepare_init}" >/dev/null
fi

source_unique_key_columns="$(db_query_value "
  SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'product_metrics'
    AND INDEX_NAME = 'uk_product_metrics_date_product'
    AND NON_UNIQUE = 0
")"
[[ "${source_unique_key_columns}" == "metric_date,product_id" ]] \
  || fail "product_metrics에 UNIQUE (metric_date, product_id)가 필요합니다. 현재: ${source_unique_key_columns:-없음}"

if [[ "${PREPARE_DATASET}" == "true" ]]; then
  printf '합성 데이터 생성: %s일 x 일간 %s행\n' "${DAYS}" "${DAILY_PRODUCTS}"
  seed_started_at="$(date +%s)"
  for ((day_offset = 0; day_offset < DAYS; day_offset++)); do
    seed_init="SET @target_date = STR_TO_DATE('${TARGET_DATE}', '%Y%m%d'), @day_offset = ${day_offset}, @daily_products = ${DAILY_PRODUCTS}, @product_id_base = ${PRODUCT_ID_BASE}, @data_seed = ${DATA_SEED};"
    db_execute_file "${SQL_DIR}/seed-day.sql" "${seed_init}" >/dev/null
    printf '  - day %s/%s 적재 완료\n' "$((day_offset + 1))" "${DAYS}"
  done
  seed_elapsed_seconds="$(( $(date +%s) - seed_started_at ))"
else
  printf '기존 합성 데이터를 검증 후 재사용합니다.\n'
fi

expected_source_rows="$(( DAYS * DAILY_PRODUCTS ))"
actual_dataset_shape="$(db_query_value "
  SELECT
    COUNT(*),
    COUNT(DISTINCT metric_date),
    COUNT(DISTINCT product_id),
    MIN(metric_date),
    MAX(metric_date)
  FROM product_metrics
  WHERE metric_date BETWEEN DATE_SUB(STR_TO_DATE('${TARGET_DATE}', '%Y%m%d'), INTERVAL $((DAYS - 1)) DAY)
                        AND STR_TO_DATE('${TARGET_DATE}', '%Y%m%d')
    AND product_id > ${PRODUCT_ID_BASE}
    AND product_id <= ${PRODUCT_ID_BASE} + ${DAILY_PRODUCTS}
")"
expected_first_date="$(db_server_query_value "SELECT DATE_FORMAT(DATE_SUB(STR_TO_DATE('${TARGET_DATE}', '%Y%m%d'), INTERVAL $((DAYS - 1)) DAY), '%Y-%m-%d')")"
expected_last_date="$(db_server_query_value "SELECT DATE_FORMAT(STR_TO_DATE('${TARGET_DATE}', '%Y%m%d'), '%Y-%m-%d')")"
expected_dataset_shape="${expected_source_rows}"$'\t'"${DAYS}"$'\t'"${DAILY_PRODUCTS}"$'\t'"${expected_first_date}"$'\t'"${expected_last_date}"
[[ "${actual_dataset_shape}" == "${expected_dataset_shape}" ]] \
  || fail "합성 데이터 형태가 다릅니다. expected=${expected_dataset_shape}, actual=${actual_dataset_shape}"

db_query_report "
  SELECT
    COUNT(*) AS source_rows,
    COUNT(DISTINCT metric_date) AS metric_dates,
    COUNT(DISTINCT product_id) AS products,
    MIN(metric_date) AS first_metric_date,
    MAX(metric_date) AS last_metric_date
  FROM product_metrics
  WHERE product_id > ${PRODUCT_ID_BASE}
    AND product_id <= ${PRODUCT_ID_BASE} + ${DAILY_PRODUCTS}
" > "${OUTPUT_DIR}/dataset.tsv"
printf 'seed_elapsed_seconds=%s\n' "${seed_elapsed_seconds}" >> "${OUTPUT_DIR}/manifest.env"

if [[ "${BUILD_IMAGE}" == "true" ]]; then
  ./gradlew :apps:commerce-batch:bootJar
  docker build \
    --file apps/commerce-batch/Dockerfile \
    --tag "${BATCH_IMAGE}" \
    .
fi

docker image inspect "${BATCH_IMAGE}" >/dev/null
image_metadata="$(docker image inspect --format '{{.Id}}|{{.Created}}' "${BATCH_IMAGE}")"
IFS='|' read -r image_id image_created_at <<< "${image_metadata}"
{
  printf 'image_id=%s\n' "${image_id}"
  printf 'created_at=%s\n' "${image_created_at}"
} >> "${OUTPUT_DIR}/manifest.env"

MYSQL_DATABASE="${PERFORMANCE_DATABASE}"
PERF_CPUS="${CONTAINER_CPUS}"
PERF_MEMORY="${CONTAINER_MEMORY}"
export BATCH_IMAGE MYSQL_DATABASE MYSQL_USER MYSQL_PWD PERF_CPUS PERF_MEMORY
"${COMPOSE_BATCH[@]}" config --quiet

db_execute_file "${SQL_DIR}/global-status.sql" > "${OUTPUT_DIR}/mysql-status-before.tsv"

sample_container() {
  local container_id="$1"
  local output_file="$2"

  printf 'observed_at_utc\tcpu_percent\tmemory_usage\tmemory_percent\tblock_io\tnetwork_io\tpids\n' \
    > "${output_file}"
  while [[ "$(docker container inspect --format '{{.State.Running}}' "${container_id}" 2>/dev/null || true)" == "true" ]]; do
    printf '%s\t' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" >> "${output_file}"
    docker stats \
      --no-stream \
      --format '{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.BlockIO}}\t{{.NetIO}}\t{{.PIDs}}' \
      "${container_id}" >> "${output_file}" || true
    sleep "${SAMPLE_INTERVAL_SECONDS}"
  done
}

container_name="perf-product-ranking-${RUN_KEY}"
batch_started_epoch="$(date +%s)"
CONTAINER_ID="$(
  "${COMPOSE_BATCH[@]}" run \
    --detach \
    --no-deps \
    --name "${container_name}" \
    --env "PRODUCT_RANKING_CHUNK_SIZE=${CHUNK_SIZE}" \
    --env "PRODUCT_RANKING_CHUNK_LOG_INTERVAL=${CHUNK_LOG_INTERVAL}" \
    product-ranking-batch \
    "targetDate=${TARGET_DATE}" \
    "rebuildSequence=${REBUILD_SEQUENCE}"
)"

sample_container "${CONTAINER_ID}" "${OUTPUT_DIR}/container-stats.tsv" &
sampler_pid=$!
batch_exit_code="$(docker wait "${CONTAINER_ID}")"
wait "${sampler_pid}" || true
batch_elapsed_seconds="$(( $(date +%s) - batch_started_epoch ))"

docker logs "${CONTAINER_ID}" > "${OUTPUT_DIR}/batch.log" 2>&1 || true
awk \
  -f "${SCRIPT_DIR}/extract-chunk-metrics.awk" \
  "${OUTPUT_DIR}/batch.log" \
  > "${OUTPUT_DIR}/chunk-metrics.csv"
container_result="$(
  docker container inspect \
    --format '{{.State.StartedAt}}|{{.State.FinishedAt}}|{{.State.ExitCode}}|{{.State.OOMKilled}}' \
    "${CONTAINER_ID}"
)"
IFS='|' read -r container_started_at container_finished_at container_exit_code container_oom_killed \
  <<< "${container_result}"
{
  printf 'started_at\tfinished_at\texit_code\toom_killed\n'
  printf '%s\t%s\t%s\t%s\n' \
    "${container_started_at}" \
    "${container_finished_at}" \
    "${container_exit_code}" \
    "${container_oom_killed}"
} > "${OUTPUT_DIR}/container-result.tsv"
docker container rm "${CONTAINER_ID}" >/dev/null
CONTAINER_ID=""

{
  printf 'batch_exit_code=%s\n' "${batch_exit_code}"
  printf 'batch_elapsed_seconds=%s\n' "${batch_elapsed_seconds}"
  printf 'container_finished_at_utc=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
} >> "${OUTPUT_DIR}/manifest.env"

if db_execute_file "${SQL_DIR}/global-status.sql" \
  > "${OUTPUT_DIR}/mysql-status-after.tsv" \
  2>> "${OUTPUT_DIR}/collection-errors.log"; then
  awk \
    -f "${SCRIPT_DIR}/status-delta.awk" \
    "${OUTPUT_DIR}/mysql-status-before.tsv" \
    "${OUTPUT_DIR}/mysql-status-after.tsv" \
    > "${OUTPUT_DIR}/mysql-status-delta.tsv"
fi

report_init="SET @target_date_value = '${TARGET_DATE}', @target_date = STR_TO_DATE('${TARGET_DATE}', '%Y%m%d'), @rebuild_sequence = '${REBUILD_SEQUENCE}', @product_id_base = ${PRODUCT_ID_BASE}, @daily_products = ${DAILY_PRODUCTS};"
db_execute_file "${SQL_DIR}/database-report.sql" "${report_init}" \
  > "${OUTPUT_DIR}/database-report.tsv" \
  2>> "${OUTPUT_DIR}/collection-errors.log" || true

db_execute_file_no_headers "${SQL_DIR}/job-execution.csv.sql" "${report_init}" \
  > "${OUTPUT_DIR}/job-execution.csv" \
  2>> "${OUTPUT_DIR}/collection-errors.log" || true
db_execute_file_no_headers "${SQL_DIR}/step-executions.csv.sql" "${report_init}" \
  > "${OUTPUT_DIR}/step-executions.csv" \
  2>> "${OUTPUT_DIR}/collection-errors.log" || true

job_status="$(db_query_value "
  SELECT je.STATUS
  FROM BATCH_JOB_EXECUTION je
  JOIN BATCH_JOB_INSTANCE ji ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID
  JOIN BATCH_JOB_EXECUTION_PARAMS target_date
    ON target_date.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID
   AND target_date.PARAMETER_NAME = 'targetDate'
   AND target_date.PARAMETER_VALUE = '${TARGET_DATE}'
  JOIN BATCH_JOB_EXECUTION_PARAMS rebuild
    ON rebuild.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID
   AND rebuild.PARAMETER_NAME = 'rebuildSequence'
   AND rebuild.PARAMETER_VALUE = '${REBUILD_SEQUENCE}'
  WHERE ji.JOB_NAME = 'productRankingAggregationJob'
  ORDER BY je.JOB_EXECUTION_ID DESC
  LIMIT 1
" 2>> "${OUTPUT_DIR}/collection-errors.log" || true)"

expected_rank_count=100
if (( DAILY_PRODUCTS < expected_rank_count )); then
  expected_rank_count="${DAILY_PRODUCTS}"
fi
weekly_rank_count="$(db_query_value "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE aggregation_date = STR_TO_DATE('${TARGET_DATE}', '%Y%m%d')" 2>> "${OUTPUT_DIR}/collection-errors.log" || true)"
monthly_rank_count="$(db_query_value "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE aggregation_date = STR_TO_DATE('${TARGET_DATE}', '%Y%m%d')" 2>> "${OUTPUT_DIR}/collection-errors.log" || true)"

{
  printf 'job_status=%s\n' "${job_status:-NOT_FOUND}"
  printf 'weekly_rank_count=%s\n' "${weekly_rank_count:-NOT_COLLECTED}"
  printf 'monthly_rank_count=%s\n' "${monthly_rank_count:-NOT_COLLECTED}"
  printf 'finished_at_utc=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
} >> "${OUTPUT_DIR}/manifest.env"

printf '\n성능 테스트 결과: %s\n' "${OUTPUT_DIR}"
printf '  Batch elapsed: %ss\n' "${batch_elapsed_seconds}"
printf '  Batch status: %s (container exit %s)\n' "${job_status:-NOT_FOUND}" "${batch_exit_code}"
printf '  Snapshot rows: weekly=%s, monthly=%s\n' "${weekly_rank_count:-NOT_COLLECTED}" "${monthly_rank_count:-NOT_COLLECTED}"

[[ "${batch_exit_code}" == "0" ]] || fail "Batch 컨테이너가 실패했습니다. batch.log를 확인하세요."
[[ "${job_status}" == "COMPLETED" ]] || fail "Spring Batch 상태가 COMPLETED가 아닙니다: ${job_status:-NOT_FOUND}"
[[ "${weekly_rank_count}" == "${expected_rank_count}" ]] \
  || fail "주간 스냅샷 행 수가 다릅니다. expected=${expected_rank_count}, actual=${weekly_rank_count}"
[[ "${monthly_rank_count}" == "${expected_rank_count}" ]] \
  || fail "월간 스냅샷 행 수가 다릅니다. expected=${expected_rank_count}, actual=${monthly_rank_count}"
