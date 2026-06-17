#!/usr/bin/env bash
# =============================================================================
# 인덱스 최적화 벤치마크 러너
# =============================================================================
# 전제: docker-compose -f ./docker/infra-compose.yml up -d 로 MySQL 이 떠 있어야 함.
#
# 실행 순서:
#   01_schema   → 02_seed   → 03_explain_before
#   → 04_indexes → 05_explain_after
# 각 EXPLAIN 결과는 benchmark/results/ 에 저장한다.
#
# 사용:  bash benchmark/run.sh
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_DIR="$SCRIPT_DIR/sql"
OUT_DIR="$SCRIPT_DIR/results"
mkdir -p "$OUT_DIR"

# MySQL 컨테이너 이름 자동 탐색 (compose 프로젝트명에 따라 docker-mysql-1 / docker_mysql_1 등)
CONTAINER="$(docker ps --filter "ancestor=mysql:8.0" --format '{{.Names}}' | head -n1)"
if [[ -z "$CONTAINER" ]]; then
  echo "ERROR: mysql:8.0 컨테이너를 찾을 수 없습니다. 먼저 인프라를 띄우세요:" >&2
  echo "  docker-compose -f ./docker/infra-compose.yml up -d" >&2
  exit 1
fi
echo "MySQL 컨테이너: $CONTAINER"

run_sql() {  # run_sql <file> [outfile]
  local file="$1"
  echo ">>> $(basename "$file")"
  if [[ -n "${2:-}" ]]; then
    docker exec -i "$CONTAINER" mysql -uroot -proot --table < "$file" | tee "$2"
  else
    docker exec -i "$CONTAINER" mysql -uroot -proot --table < "$file"
  fi
}

run_sql "$SQL_DIR/01_schema.sql"
run_sql "$SQL_DIR/02_seed.sql"           "$OUT_DIR/02_seed_summary.txt"
run_sql "$SQL_DIR/03_explain_before.sql" "$OUT_DIR/03_explain_before.txt"
run_sql "$SQL_DIR/04_indexes.sql"        "$OUT_DIR/04_indexes.txt"
run_sql "$SQL_DIR/05_explain_after.sql"  "$OUT_DIR/05_explain_after.txt"

echo
echo "완료. 결과: $OUT_DIR"
