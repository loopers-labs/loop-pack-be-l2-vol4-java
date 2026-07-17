#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${REPO_DIR}/docker/ranking-kafka-e2e-compose.yml"
COMPOSE_PROJECT="loopers-ranking-kafka-e2e"
REPORT_DIR="${REPO_DIR}/build/reports/ranking-kafka-e2e/$(date +%Y%m%d-%H%M%S)"
LOCK_DIR="${TMPDIR:-/tmp}/loopers-ranking-kafka-e2e.lock"

API_PORT=18080
API_MANAGEMENT_PORT=18081
STREAMER_PORT=18082
STREAMER_MANAGEMENT_PORT=18083
MYSQL_PORT=13306
REDIS_MASTER_PORT=16379
REDIS_REPLICA_PORT=16380
KAFKA_PORT=19093
DATABASE_NAME="loopers_ranking_e2e"
TOPIC="ranking-e2e-catalog-events"
RANKING_GROUP="ranking-e2e-ranking"
METRICS_GROUP="ranking-e2e-metrics"
WAIT_TIMEOUT_SECONDS="${RANKING_E2E_TIMEOUT_SECONDS:-90}"

E2E_JAVA="${E2E_JAVA:?E2E_JAVA must point to the Java 21 executable}"
E2E_API_JAR="${E2E_API_JAR:?E2E_API_JAR must point to the commerce-api bootJar}"
E2E_STREAMER_JAR="${E2E_STREAMER_JAR:?E2E_STREAMER_JAR must point to the commerce-streamer bootJar}"

COMPOSE=(docker compose -p "${COMPOSE_PROJECT}" -f "${COMPOSE_FILE}")
API_PID=""
STREAMER_PID=""
LOCK_ACQUIRED=0

mkdir -p "${REPORT_DIR}"

log() {
  printf '[ranking-kafka-e2e] %s\n' "$*"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "required command not found: $1"
    exit 1
  fi
}

assert_port_available() {
  local port="$1"
  if lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1; then
    log "port ${port} is already in use; parallel E2E runs are not supported"
    exit 1
  fi
}

stop_process() {
  local pid="$1"
  if [[ -z "${pid}" ]] || ! kill -0 "${pid}" >/dev/null 2>&1; then
    return
  fi

  kill "${pid}" >/dev/null 2>&1 || true
  for _ in {1..20}; do
    if ! kill -0 "${pid}" >/dev/null 2>&1; then
      wait "${pid}" >/dev/null 2>&1 || true
      return
    fi
    sleep 0.25
  done
  kill -9 "${pid}" >/dev/null 2>&1 || true
  wait "${pid}" >/dev/null 2>&1 || true
}

diagnostics() {
  set +e
  log "failure diagnostics: ${REPORT_DIR}"
  "${COMPOSE[@]}" ps >"${REPORT_DIR}/compose-ps.log" 2>&1
  "${COMPOSE[@]}" logs --no-color >"${REPORT_DIR}/compose.log" 2>&1
  "${COMPOSE[@]}" exec -T mysql mysql -uapplication -papplication -D "${DATABASE_NAME}" -e \
    "SELECT id, event_type, status, fail_reason, created_at, published_at FROM outbox_event ORDER BY id DESC LIMIT 10" \
    >"${REPORT_DIR}/outbox.log" 2>&1
  "${COMPOSE[@]}" exec -T kafka /opt/bitnami/kafka/bin/kafka-consumer-groups.sh \
    --bootstrap-server localhost:9092 --describe --group "${RANKING_GROUP}" \
    >"${REPORT_DIR}/consumer-group.log" 2>&1
  if [[ -n "${RANKING_KEY:-}" ]]; then
    "${COMPOSE[@]}" exec -T redis-master redis-cli ZREVRANGE "${RANKING_KEY}" 0 20 WITHSCORES \
      >"${REPORT_DIR}/redis-ranking.log" 2>&1
  fi
  tail -n 120 "${REPORT_DIR}/streamer.log" >&2 2>/dev/null || true
  tail -n 120 "${REPORT_DIR}/api.log" >&2 2>/dev/null || true
}

cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM
  set +e
  if (( exit_code != 0 && LOCK_ACQUIRED == 1 )); then
    diagnostics
  fi
  if (( LOCK_ACQUIRED == 1 )); then
    stop_process "${API_PID}"
    stop_process "${STREAMER_PID}"
    "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1
    rmdir "${LOCK_DIR}" >/dev/null 2>&1 || true
  fi
  if (( exit_code != 0 )); then
    log "FAILED; reports kept at ${REPORT_DIR}"
  fi
  exit "${exit_code}"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

wait_until() {
  local description="$1"
  shift
  local deadline=$((SECONDS + WAIT_TIMEOUT_SECONDS))
  until "$@"; do
    if [[ -n "${STREAMER_PID}" ]] && ! kill -0 "${STREAMER_PID}" >/dev/null 2>&1; then
      log "commerce-streamer exited while waiting for ${description}"
      return 1
    fi
    if [[ -n "${API_PID}" ]] && ! kill -0 "${API_PID}" >/dev/null 2>&1; then
      log "commerce-api exited while waiting for ${description}"
      return 1
    fi
    if (( SECONDS >= deadline )); then
      log "timed out waiting for ${description}"
      return 1
    fi
    sleep 0.5
  done
  log "ready: ${description}"
}

infra_healthy() {
  [[ "$("${COMPOSE[@]}" ps --services --filter status=running | wc -l | tr -d ' ')" == "4" ]] || return 1
  "${COMPOSE[@]}" exec -T mysql mysqladmin ping -h localhost -uroot -proot --silent >/dev/null 2>&1 || return 1
  "${COMPOSE[@]}" exec -T redis-master redis-cli PING 2>/dev/null | grep -q PONG || return 1
  "${COMPOSE[@]}" exec -T redis-readonly redis-cli PING 2>/dev/null | grep -q PONG || return 1
  "${COMPOSE[@]}" exec -T kafka /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list >/dev/null 2>&1
}

streamer_ready() {
  local response
  response="$(curl -fsS "http://localhost:${STREAMER_MANAGEMENT_PORT}/actuator/health/readiness" 2>/dev/null)" || return 1
  python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin).get("status") == "UP" else 1)' \
    <<<"${response}" >/dev/null 2>&1 || return 1
  "${COMPOSE[@]}" exec -T kafka /opt/bitnami/kafka/bin/kafka-consumer-groups.sh \
    --bootstrap-server localhost:9092 --describe --group "${RANKING_GROUP}" 2>/dev/null | grep -q "${TOPIC}"
}

api_ready() {
  local response
  response="$(curl -fsS "http://localhost:${API_MANAGEMENT_PORT}/actuator/health/readiness" 2>/dev/null)" || return 1
  python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin).get("status") == "UP" else 1)' \
    <<<"${response}" >/dev/null 2>&1
}

outbox_published() {
  local status
  status="$("${COMPOSE[@]}" exec -T mysql mysql -uapplication -papplication -N -s -D "${DATABASE_NAME}" -e \
    "SELECT status FROM outbox_event WHERE event_type = 'PRODUCT_VIEWED' ORDER BY id DESC LIMIT 1" 2>/dev/null | tr -d '\r')"
  [[ "${status}" == "PUBLISHED" ]]
}

redis_score_matches() {
  local score
  score="$("${COMPOSE[@]}" exec -T redis-master redis-cli --raw ZSCORE "${RANKING_KEY}" "${PRODUCT_ID}" 2>/dev/null | tr -d '\r')"
  python3 - "${score}" <<'PY'
import math
import sys

try:
    score = float(sys.argv[1])
except (IndexError, ValueError):
    raise SystemExit(1)
raise SystemExit(0 if math.isclose(score, 0.1, rel_tol=0.0, abs_tol=1e-9) else 1)
PY
}

ranking_api_matches() {
  curl -fsS "http://localhost:${API_PORT}/api/v1/rankings?date=${RANKING_DATE}&page=1&size=20" \
    >"${REPORT_DIR}/ranking-response.json" || return 1
  python3 - "${REPORT_DIR}/ranking-response.json" "${PRODUCT_ID}" <<'PY'
import json
import math
import sys

with open(sys.argv[1], encoding="utf-8") as response_file:
    response = json.load(response_file)
product_id = int(sys.argv[2])
items = [item for item in response.get("data", []) if item.get("product", {}).get("id") == product_id]
if len(items) != 1:
    raise SystemExit(1)
item = items[0]
valid = item.get("rank") == 1 and math.isclose(float(item.get("score")), 0.1, rel_tol=0.0, abs_tol=1e-9)
raise SystemExit(0 if valid else 1)
PY
}

for command in docker curl python3 lsof; do
  require_command "${command}"
done
docker compose version >/dev/null
"${E2E_JAVA}" -version 2>&1 | tee "${REPORT_DIR}/java-version.log"
if ! grep -Eq 'version "21([.]|\")' "${REPORT_DIR}/java-version.log"; then
  log "E2E_JAVA must be Java 21: ${E2E_JAVA}"
  exit 1
fi
[[ -f "${E2E_API_JAR}" ]]
[[ -f "${E2E_STREAMER_JAR}" ]]

if ! mkdir "${LOCK_DIR}" >/dev/null 2>&1; then
  log "another ranking Kafka E2E run is active, or a stale lock exists: ${LOCK_DIR}"
  exit 1
fi
LOCK_ACQUIRED=1

"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
for port in "${API_PORT}" "${API_MANAGEMENT_PORT}" "${STREAMER_PORT}" "${STREAMER_MANAGEMENT_PORT}" \
  "${MYSQL_PORT}" "${REDIS_MASTER_PORT}" "${REDIS_REPLICA_PORT}" "${KAFKA_PORT}"; do
  assert_port_available "${port}"
done

log "starting isolated MySQL, Redis, and Kafka"
"${COMPOSE[@]}" up -d
wait_until "isolated infrastructure" infra_healthy
"${COMPOSE[@]}" exec -T kafka /opt/bitnami/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --create --if-not-exists --topic "${TOPIC}" --partitions 3 --replication-factor 1 \
  >"${REPORT_DIR}/topic-create.log"

COMMON_ARGS=(
  "--spring.profiles.active=local"
  "--spring.jpa.hibernate.ddl-auto=update"
  "--spring.jpa.show-sql=false"
  "--datasource.mysql-jpa.main.jdbc-url=jdbc:mysql://localhost:${MYSQL_PORT}/${DATABASE_NAME}"
  "--datasource.mysql-jpa.main.username=application"
  "--datasource.mysql-jpa.main.password=application"
  "--datasource.mysql-jpa.main.minimum-idle=2"
  "--datasource.mysql-jpa.main.maximum-pool-size=5"
  "--datasource.redis.database=0"
  "--datasource.redis.master.host=localhost"
  "--datasource.redis.master.port=${REDIS_MASTER_PORT}"
  "--datasource.redis.replicas[0].host=localhost"
  "--datasource.redis.replicas[0].port=${REDIS_REPLICA_PORT}"
  "--spring.kafka.bootstrap-servers=localhost:${KAFKA_PORT}"
  "--spring.kafka.admin.properties.bootstrap.servers=localhost:${KAFKA_PORT}"
  "--spring.kafka.consumer.auto-offset-reset=earliest"
  "--loopers.kafka.topics.catalog-events=${TOPIC}"
  "--logging.level.org.hibernate.SQL=OFF"
)

log "starting commerce-streamer as a separate JVM"
"${E2E_JAVA}" -jar "${E2E_STREAMER_JAR}" \
  "${COMMON_ARGS[@]}" \
  "--server.port=${STREAMER_PORT}" \
  "--server.address=127.0.0.1" \
  "--management.server.port=${STREAMER_MANAGEMENT_PORT}" \
  "--management.server.address=127.0.0.1" \
  "--loopers.kafka.consumer-groups.catalog-ranking=${RANKING_GROUP}" \
  "--loopers.kafka.consumer-groups.catalog-metrics=${METRICS_GROUP}" \
  >"${REPORT_DIR}/streamer.log" 2>&1 &
STREAMER_PID=$!
wait_until "streamer readiness and ranking consumer assignment" streamer_ready

log "starting commerce-api as a separate JVM"
"${E2E_JAVA}" -jar "${E2E_API_JAR}" \
  "${COMMON_ARGS[@]}" \
  "--server.port=${API_PORT}" \
  "--server.address=127.0.0.1" \
  "--management.server.port=${API_MANAGEMENT_PORT}" \
  "--management.server.address=127.0.0.1" \
  "--loopers.outbox.relay-enabled=true" \
  "--loopers.outbox.relay-delay=100ms" \
  "--loopers.waiting-queue.scheduler-enabled=false" \
  "--loopers.payment.reconciliation-enabled=false" \
  >"${REPORT_DIR}/api.log" 2>&1 &
API_PID=$!
wait_until "API readiness" api_ready

RUN_ID="$(date +%s)"
curl -fsS -X POST "http://localhost:${API_PORT}/api-admin/v1/brands" \
  -H 'Content-Type: application/json' \
  -H 'X-Loopers-Ldap: loopers.admin' \
  -d "{\"name\":\"ranking-e2e-${RUN_ID}\",\"description\":\"ranking kafka e2e\"}" \
  >"${REPORT_DIR}/brand-response.json"
BRAND_ID="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["data"]["id"])' "${REPORT_DIR}/brand-response.json")"

curl -fsS -X POST "http://localhost:${API_PORT}/api-admin/v1/products" \
  -H 'Content-Type: application/json' \
  -H 'X-Loopers-Ldap: loopers.admin' \
  -d "{\"brandId\":${BRAND_ID},\"name\":\"ranking-e2e-product-${RUN_ID}\",\"description\":\"ranking kafka e2e\",\"price\":10000,\"stock\":10}" \
  >"${REPORT_DIR}/product-response.json"
PRODUCT_ID="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["data"]["id"])' "${REPORT_DIR}/product-response.json")"

RANKING_DATE="$(TZ=Asia/Seoul date +%Y%m%d)"
RANKING_KEY="ranking:all:${RANKING_DATE}"
log "triggering PRODUCT_VIEWED for product ${PRODUCT_ID}"
curl -fsS "http://localhost:${API_PORT}/api/v1/products/${PRODUCT_ID}" >"${REPORT_DIR}/product-view-response.json"

wait_until "outbox event publication" outbox_published
wait_until "Redis score 0.1" redis_score_matches
wait_until "Ranking API rank=1 and score=0.1" ranking_api_matches

cat >"${REPORT_DIR}/result.txt" <<EOF
status=PASS
topic=${TOPIC}
consumer_group=${RANKING_GROUP}
ranking_key=${RANKING_KEY}
product_id=${PRODUCT_ID}
expected_rank=1
expected_score=0.1
EOF

log "PASS: Outbox -> Kafka -> streamer -> Redis -> Ranking API"
log "report: ${REPORT_DIR}/result.txt"
