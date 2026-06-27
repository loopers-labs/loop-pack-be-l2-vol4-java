#!/usr/bin/env bash
# Stage 1 측정용 시드.
# commerce-api(local, ddl-auto:create) 부팅 직후 1회 실행한다.
#   1) 회원가입 API 로 테스트 유저 생성 → 평문 비밀번호 확보(bcrypt 해시 역산 회피)
#   2) brands/products/orders 를 raw SQL 로 적재(결제는 OrderModel 만 읽으므로 order_items 불필요)
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-docker-mysql-1}"
LOGIN_ID="${LOGIN_ID:-looptester}"
LOGIN_PW="${LOGIN_PW:-Looptest1234}"
PRODUCT_COUNT="${PRODUCT_COUNT:-100}"
ORDER_COUNT="${ORDER_COUNT:-3000}"

echo "[seed] 회원가입 → ${LOGIN_ID}"
SIGNUP=$(curl -s -X POST "${BASE_URL}/api/v1/users" \
    -H 'Content-Type: application/json' \
    -d "{\"loginId\":\"${LOGIN_ID}\",\"password\":\"${LOGIN_PW}\",\"name\":\"측정유저\",\"birthDate\":\"1990-01-01\",\"email\":\"loop@loopers.test\"}")
echo "[seed] signup response: ${SIGNUP}"

USER_ID=$(echo "${SIGNUP}" | sed -n 's/.*"userId":\([0-9]*\).*/\1/p')
if [ -z "${USER_ID}" ]; then
    echo "[seed] ERROR: userId 를 파싱하지 못했습니다. 이미 가입된 유저면 DB 를 재생성(앱 재부팅)하세요." >&2
    exit 1
fi
echo "[seed] userId=${USER_ID}"

echo "[seed] brands/products/orders 적재 (products=${PRODUCT_COUNT}, orders=${ORDER_COUNT})"
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot loopers <<SQL
SET SESSION cte_max_recursion_depth = 1000000;

INSERT INTO brands (name, description, created_at, updated_at)
VALUES ('측정브랜드', '측정용 브랜드', NOW(), NOW());

INSERT INTO products (brand_id, name, description, price, stock, like_count, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < ${PRODUCT_COUNT}
)
SELECT 1, CONCAT('상품', n), '측정용 상품', 10000, 1000, 0, NOW(), NOW() FROM seq;

INSERT INTO orders (user_id, status, ordered_at, original_amount, discount_amount, final_amount, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < ${ORDER_COUNT}
)
SELECT ${USER_ID}, 'CREATED', NOW(), 5000, 0, 5000, NOW(), NOW() FROM seq;

SELECT
    (SELECT COUNT(*) FROM brands)   AS brands,
    (SELECT COUNT(*) FROM products) AS products,
    (SELECT COUNT(*) FROM orders WHERE status = 'CREATED') AS created_orders,
    (SELECT MIN(id) FROM orders) AS min_order_id,
    (SELECT MAX(id) FROM orders) AS max_order_id;
SQL

echo "[seed] 완료. k6 실행: BASE_URL=${BASE_URL} k6 run stage1-baseline.js"
