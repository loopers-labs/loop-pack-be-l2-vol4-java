-- =====================================================================
-- Round 5 — 읽기 성능 벤치마크용 시딩 스크립트
-- =====================================================================
-- 목적: 상품 10만 + 좋아요 약 100만(치우친 분포)을 생성해
--       brandId 필터 / price_asc / likes_desc 정렬의 병목을 재현한다.
--
-- 실행 전제:
--   1) docker/infra-compose.yml 로 MySQL(loopers) 기동
--   2) 앱을 local 프로파일로 1회 띄워 스키마 생성 (ddl-auto=create) 후 종료
--      ⚠ 앱을 다시 띄우면 테이블이 drop→recreate 되어 시딩 데이터가 날아간다.
--      시딩 이후 측정이 끝날 때까지 앱을 재기동하지 말 것.
--   3) 이 스크립트 실행:
--      docker exec -i <mysql_container> mysql -uapplication -papplication loopers < sql/round5/seed_round5.sql
--
-- 재귀 CTE: SQL이 1,2,3...을 스스로 만들어 INSERT 한 방으로 대량 행을 생성한다.
--           기본 재귀 깊이 한도(1000)를 넘기므로 세션 한도를 먼저 올린다.
-- =====================================================================

SET SESSION cte_max_recursion_depth = 2000000;

-- 멱등 실행: 재시딩 시 기존 데이터 제거 (FK 제약은 없음)
DELETE FROM likes;
DELETE FROM products;

-- ---------------------------------------------------------------------
-- 1) 상품 10만 개
--    - brand_id: 1~500 으로 분산 (브랜드 필터 카디널리티 확보)
--    - price:    1,000 ~ 1,000,000 무작위 (정렬 의미 확보)
--    - created_at: 최근 365일 분산 (latest 정렬 의미 확보)
-- ---------------------------------------------------------------------
INSERT INTO products (brand_id, name, description, price, status, created_at, updated_at)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100000
)
SELECT
    (n % 500) + 1                                        AS brand_id,
    CONCAT('상품-', n)                                   AS name,
    CONCAT('상품 설명 텍스트 ', n)                        AS description,
    FLOOR(1000 + RAND() * 999000)                        AS price,
    'ON_SALE'                                            AS status,
    NOW() - INTERVAL FLOOR(RAND() * 365) DAY             AS created_at,
    NOW()                                                AS updated_at
FROM seq;

-- ---------------------------------------------------------------------
-- 2) 좋아요 약 100만 개 (치우친 분포)
--    - 좋아요 행을 직접 100만 개 생성하고 product_id 를 작은 id 쪽에 몰리게 매핑
--      → POW(RAND(), 3): 0~1 난수를 세제곱 → 0 근처에 집중 → 작은 product_id 가 인기
--      → 소수 인기 상품이 좋아요를 독식하는 멱법칙을 흉내 (likes_desc 정렬에 의미 부여)
--    - user_id 를 1..1,000,000 으로 전부 유니크하게 부여
--      → (user_id, product_id) UK 가 절대 충돌하지 않음 (각 행의 user_id 가 다르므로)
--    - 카티시안 조인을 쓰지 않아 빠르게 적재된다
-- ---------------------------------------------------------------------
INSERT INTO likes (user_id, product_id, created_at)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 1000000
)
SELECT
    n                                                    AS user_id,
    FLOOR(POW(RAND(), 3) * 100000) + 1                   AS product_id,
    NOW() - INTERVAL FLOOR(RAND() * 365) DAY             AS created_at
FROM seq;

-- ---------------------------------------------------------------------
-- 3) 적재 결과 확인
-- ---------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM products) AS product_count,
    (SELECT COUNT(*) FROM likes)    AS like_count;

-- 좋아요 분포 상위 10개 상품 (치우침 확인용)
SELECT product_id, COUNT(*) AS likes
FROM likes
GROUP BY product_id
ORDER BY likes DESC
LIMIT 10;
