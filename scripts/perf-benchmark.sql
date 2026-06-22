-- =============================================================
-- Round 5 성능 벤치마크
-- 카디널리티 분석 + EXPLAIN (실행계획) + 실행시간 측정
-- 공통 조건: brand_id = 1, LIMIT 20
-- =============================================================
-- 실행: /usr/local/mysql-9.7.0-macos15-arm64/bin/mysql -u application -papplication loopers_test < scripts/perf-benchmark.sql
-- EXPLAIN FORMAT=TRADITIONAL → 실행계획 (type / key / rows / Extra)
-- SET @t + SELECT + 실행시간_ms  → 실제 실행시간 (ms 단위 표 출력)
-- =============================================================

USE loopers_test;
SET profiling = 1;

-- =============================================================
-- 이전 실행에서 남아있을 수 있는 인덱스 정리 (재실행 안전 보장)
-- CONTINUE HANDLER로 존재하지 않는 인덱스 에러 무시
-- =============================================================
DROP PROCEDURE IF EXISTS cleanup_bench_indexes;
DELIMITER $$
CREATE PROCEDURE cleanup_bench_indexes()
BEGIN
  DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;
  DROP INDEX idx_products_brand_id            ON products;
  DROP INDEX idx_products_brand_like          ON products;
  DROP INDEX idx_products_covering            ON products;
  DROP INDEX idx_products_brand_created       ON products;
  DROP INDEX idx_products_brand_price         ON products;
  DROP INDEX idx_products_like_count          ON products;
  DROP INDEX idx_products_like_covering       ON products;
  DROP INDEX idx_products_created_at          ON products;
  DROP INDEX idx_plv_like_count               ON product_like_view;
  DROP INDEX idx_plv_covering                 ON product_like_view;
  DROP INDEX idx_likes_member_created         ON likes;
  DROP INDEX idx_likes_member_created_product ON likes;
  DROP INDEX idx_likes_member_id              ON likes;
  DROP INDEX idx_orders_member_created        ON orders;
  DROP INDEX idx_order_items_order_product    ON order_items;
  DROP INDEX idx_stocks_product_quantity      ON stocks;
END$$
DELIMITER ;
CALL cleanup_bench_indexes();
DROP PROCEDURE IF EXISTS cleanup_bench_indexes;

-- =============================================================
-- [0] 사전 분석 — 카디널리티
-- =============================================================

-- 0-1. 컬럼별 카디널리티
SELECT
    COUNT(*)                                             AS total_rows,
    COUNT(DISTINCT brand_id)                             AS brand_distinct,
    COUNT(DISTINCT like_count)                           AS like_count_distinct,
    COUNT(DISTINCT price)                                AS price_distinct,
    ROUND(COUNT(DISTINCT brand_id)   / COUNT(*), 6)     AS brand_cardinality,
    ROUND(COUNT(DISTINCT like_count) / COUNT(*), 6)     AS like_count_cardinality,
    ROUND(COUNT(DISTINCT price)      / COUNT(*), 6)     AS price_cardinality
FROM products;

-- 0-2. 브랜드별 상품 수 분포
SELECT
    MIN(cnt) AS 최소_상품수, MAX(cnt) AS 최대_상품수, ROUND(AVG(cnt),1) AS 평균_상품수
FROM (SELECT brand_id, COUNT(*) AS cnt FROM products GROUP BY brand_id) t;

-- 0-3. like_count 분포
SELECT
    SUM(CASE WHEN like_count = 0               THEN 1 ELSE 0 END) AS 좋아요_0,
    SUM(CASE WHEN like_count BETWEEN 1  AND 10  THEN 1 ELSE 0 END) AS 좋아요_1_10,
    SUM(CASE WHEN like_count BETWEEN 11 AND 100 THEN 1 ELSE 0 END) AS 좋아요_11_100,
    SUM(CASE WHEN like_count > 100              THEN 1 ELSE 0 END) AS 좋아요_100초과,
    MIN(like_count) AS 최소, MAX(like_count) AS 최대, ROUND(AVG(like_count), 2) AS 평균
FROM products;

-- =============================================================
-- [1] Round 1 — 인덱스 없음
-- =============================================================

-- 1-1. 정규화 (JOIN + COUNT) / OFFSET 0
EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, COUNT(l.id) AS like_count
FROM products p LEFT JOIN likes l ON p.id = l.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL GROUP BY p.id ORDER BY like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, COUNT(l.id) AS like_count
FROM products p LEFT JOIN likes l ON p.id = l.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL GROUP BY p.id ORDER BY like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 1-2. 정규화 / OFFSET 500000
EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, COUNT(l.id) AS like_count
FROM products p LEFT JOIN likes l ON p.id = l.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL GROUP BY p.id ORDER BY like_count DESC LIMIT 20 OFFSET 500000;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, COUNT(l.id) AS like_count
FROM products p LEFT JOIN likes l ON p.id = l.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL GROUP BY p.id ORDER BY like_count DESC LIMIT 20 OFFSET 500000;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 1-3. 반정규화 / OFFSET 0
EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 1-4. 반정규화 / OFFSET 500000
EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 500000;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 500000;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

SHOW PROFILES;

-- =============================================================
-- [2] Round 2 — 단일 인덱스 (brand_id)
-- =============================================================

CREATE INDEX idx_products_brand_id ON products(brand_id);

-- 2-1. 정규화 / OFFSET 0
EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, COUNT(l.id) AS like_count
FROM products p LEFT JOIN likes l ON p.id = l.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL GROUP BY p.id ORDER BY like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, COUNT(l.id) AS like_count
FROM products p LEFT JOIN likes l ON p.id = l.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL GROUP BY p.id ORDER BY like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 2-2. 정규화 / OFFSET 500000
EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, COUNT(l.id) AS like_count
FROM products p LEFT JOIN likes l ON p.id = l.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL GROUP BY p.id ORDER BY like_count DESC LIMIT 20 OFFSET 500000;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, COUNT(l.id) AS like_count
FROM products p LEFT JOIN likes l ON p.id = l.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL GROUP BY p.id ORDER BY like_count DESC LIMIT 20 OFFSET 500000;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 2-3. 반정규화 / OFFSET 0
EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 2-4. 반정규화 / OFFSET 500000
EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 500000;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 500000;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

SHOW PROFILES;

-- =============================================================
-- [3] Round 3 — 복합 인덱스 (brand_id + like_count)
-- =============================================================

DROP INDEX idx_products_brand_id ON products;
CREATE INDEX idx_products_brand_like ON products(brand_id, like_count DESC);

-- 3-1. 정규화 / OFFSET 0
EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, COUNT(l.id) AS like_count
FROM products p LEFT JOIN likes l ON p.id = l.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL GROUP BY p.id ORDER BY like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, COUNT(l.id) AS like_count
FROM products p LEFT JOIN likes l ON p.id = l.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL GROUP BY p.id ORDER BY like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 3-2. 정규화 / OFFSET 500000
EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, COUNT(l.id) AS like_count
FROM products p LEFT JOIN likes l ON p.id = l.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL GROUP BY p.id ORDER BY like_count DESC LIMIT 20 OFFSET 500000;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, COUNT(l.id) AS like_count
FROM products p LEFT JOIN likes l ON p.id = l.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL GROUP BY p.id ORDER BY like_count DESC LIMIT 20 OFFSET 500000;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 3-3. 반정규화 / OFFSET 0 (Using filesort 사라지는지 확인)
EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 3-4. 반정규화 / OFFSET 500000
EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 500000;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 500000;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

SHOW PROFILES;

-- =============================================================
-- [4] Round 4 — 커버링 인덱스
-- =============================================================

DROP INDEX idx_products_brand_like ON products;
CREATE INDEX idx_products_covering ON products(brand_id, like_count DESC, id, name, price);

-- 4-1. 정규화 / OFFSET 0
EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, COUNT(l.id) AS like_count
FROM products p LEFT JOIN likes l ON p.id = l.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL GROUP BY p.id ORDER BY like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, COUNT(l.id) AS like_count
FROM products p LEFT JOIN likes l ON p.id = l.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL GROUP BY p.id ORDER BY like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 4-2. 정규화 / OFFSET 500000
EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, COUNT(l.id) AS like_count
FROM products p LEFT JOIN likes l ON p.id = l.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL GROUP BY p.id ORDER BY like_count DESC LIMIT 20 OFFSET 500000;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, COUNT(l.id) AS like_count
FROM products p LEFT JOIN likes l ON p.id = l.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL GROUP BY p.id ORDER BY like_count DESC LIMIT 20 OFFSET 500000;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 4-3. 반정규화 / OFFSET 0 (Extra: Using index 여부)
EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 4-4. 반정규화 / OFFSET 500000
EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 500000;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 500000;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

SHOW PROFILES;
DROP INDEX idx_products_covering ON products;

-- =============================================================
-- [5] Round 5 — 분리 테이블 (product_like_view)
-- =============================================================

CREATE TABLE product_like_view (
    product_id BIGINT PRIMARY KEY,
    like_count INT NOT NULL DEFAULT 0,
    version    BIGINT NOT NULL DEFAULT 0
);

INSERT INTO product_like_view (product_id, like_count)
SELECT product_id, COUNT(*) FROM likes GROUP BY product_id;

-- 5-1. 인덱스 없음 / OFFSET 0
EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM products p INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY plv.like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM products p INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY plv.like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 5-2. 인덱스 없음 / OFFSET 500000
EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM products p INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY plv.like_count DESC LIMIT 20 OFFSET 500000;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM products p INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY plv.like_count DESC LIMIT 20 OFFSET 500000;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 5-3. 단일 인덱스 (brand_id)
CREATE INDEX idx_products_brand_id ON products(brand_id);

EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM products p INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY plv.like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM products p INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY plv.like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM products p INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY plv.like_count DESC LIMIT 20 OFFSET 500000;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM products p INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY plv.like_count DESC LIMIT 20 OFFSET 500000;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 5-4. 복합 인덱스
DROP INDEX idx_products_brand_id ON products;
CREATE INDEX idx_products_brand_like ON products(brand_id, like_count DESC);
CREATE INDEX idx_plv_like_count ON product_like_view(like_count DESC);

EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM products p INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY plv.like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM products p INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY plv.like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM products p INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY plv.like_count DESC LIMIT 20 OFFSET 500000;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM products p INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY plv.like_count DESC LIMIT 20 OFFSET 500000;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 5-5. 커버링 인덱스
DROP INDEX idx_products_brand_like ON products;
DROP INDEX idx_plv_like_count ON product_like_view;
CREATE INDEX idx_products_covering ON products(brand_id, id, name, price);
CREATE INDEX idx_plv_covering ON product_like_view(like_count DESC, product_id);

EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM products p INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY plv.like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM products p INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY plv.like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM products p INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY plv.like_count DESC LIMIT 20 OFFSET 500000;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM products p INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY plv.like_count DESC LIMIT 20 OFFSET 500000;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

SHOW PROFILES;
DROP INDEX idx_products_covering ON products;
DROP INDEX idx_plv_covering ON product_like_view;
DROP TABLE product_like_view;

-- =============================================================
-- [6] 다양한 정렬 조건
-- =============================================================

-- 6-1. 최신순 / 인덱스 없음
EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count, created_at
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count, created_at
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 6-2. 최신순 / 복합 인덱스
CREATE INDEX idx_products_brand_created ON products(brand_id, created_at DESC);

EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count, created_at
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count, created_at
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

DROP INDEX idx_products_brand_created ON products;

-- 6-3. 가격 낮은순 / 인덱스 없음
EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY price ASC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY price ASC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 6-4. 가격 낮은순 / 복합 인덱스
CREATE INDEX idx_products_brand_price ON products(brand_id, price ASC);

EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY price ASC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY price ASC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

DROP INDEX idx_products_brand_price ON products;

SHOW PROFILES;

-- =============================================================
-- [7] 특정 사용자 좋아요 목록
-- =============================================================

-- 7-1. 인덱스 없음
EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, p.like_count, l.created_at AS liked_at
FROM likes l INNER JOIN products p ON l.product_id = p.id
WHERE l.member_id = 1 AND l.deleted_at IS NULL AND p.deleted_at IS NULL ORDER BY l.created_at DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, p.like_count, l.created_at AS liked_at
FROM likes l INNER JOIN products p ON l.product_id = p.id
WHERE l.member_id = 1 AND l.deleted_at IS NULL AND p.deleted_at IS NULL ORDER BY l.created_at DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 7-2. 복합 인덱스 (member_id + created_at)
CREATE INDEX idx_likes_member_created ON likes(member_id, created_at DESC);

EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, p.like_count, l.created_at AS liked_at
FROM likes l INNER JOIN products p ON l.product_id = p.id
WHERE l.member_id = 1 AND l.deleted_at IS NULL AND p.deleted_at IS NULL ORDER BY l.created_at DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, p.like_count, l.created_at AS liked_at
FROM likes l INNER JOIN products p ON l.product_id = p.id
WHERE l.member_id = 1 AND l.deleted_at IS NULL AND p.deleted_at IS NULL ORDER BY l.created_at DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 7-3. 커버링 인덱스 (member_id + created_at + product_id)
DROP INDEX idx_likes_member_created ON likes;
CREATE INDEX idx_likes_member_created_product ON likes(member_id, created_at DESC, product_id);

EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, p.like_count, l.created_at AS liked_at
FROM likes l INNER JOIN products p ON l.product_id = p.id
WHERE l.member_id = 1 AND l.deleted_at IS NULL AND p.deleted_at IS NULL ORDER BY l.created_at DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, p.like_count, l.created_at AS liked_at
FROM likes l INNER JOIN products p ON l.product_id = p.id
WHERE l.member_id = 1 AND l.deleted_at IS NULL AND p.deleted_at IS NULL ORDER BY l.created_at DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

DROP INDEX idx_likes_member_created_product ON likes;

SHOW PROFILES;

-- =============================================================
-- [8] 특정 사용자 구매 목록
-- =============================================================

-- 8-1. 인덱스 없음
EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, oi.quantity, o.created_at AS ordered_at
FROM orders o INNER JOIN order_items oi ON o.id = oi.order_id INNER JOIN products p ON oi.product_id = p.id
WHERE o.member_id = 1 AND o.deleted_at IS NULL AND o.status = 'CONFIRMED' AND p.deleted_at IS NULL
ORDER BY o.created_at DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, oi.quantity, o.created_at AS ordered_at
FROM orders o INNER JOIN order_items oi ON o.id = oi.order_id INNER JOIN products p ON oi.product_id = p.id
WHERE o.member_id = 1 AND o.deleted_at IS NULL AND o.status = 'CONFIRMED' AND p.deleted_at IS NULL
ORDER BY o.created_at DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 8-2. orders 복합 인덱스 (member_id + created_at)
CREATE INDEX idx_orders_member_created ON orders(member_id, created_at DESC);

EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, oi.quantity, o.created_at AS ordered_at
FROM orders o INNER JOIN order_items oi ON o.id = oi.order_id INNER JOIN products p ON oi.product_id = p.id
WHERE o.member_id = 1 AND o.deleted_at IS NULL AND o.status = 'CONFIRMED' AND p.deleted_at IS NULL
ORDER BY o.created_at DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, oi.quantity, o.created_at AS ordered_at
FROM orders o INNER JOIN order_items oi ON o.id = oi.order_id INNER JOIN products p ON oi.product_id = p.id
WHERE o.member_id = 1 AND o.deleted_at IS NULL AND o.status = 'CONFIRMED' AND p.deleted_at IS NULL
ORDER BY o.created_at DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 8-3. order_items 인덱스 추가 (order_id + product_id)
CREATE INDEX idx_order_items_order_product ON order_items(order_id, product_id);

EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, oi.quantity, o.created_at AS ordered_at
FROM orders o INNER JOIN order_items oi ON o.id = oi.order_id INNER JOIN products p ON oi.product_id = p.id
WHERE o.member_id = 1 AND o.deleted_at IS NULL AND o.status = 'CONFIRMED' AND p.deleted_at IS NULL
ORDER BY o.created_at DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, oi.quantity, o.created_at AS ordered_at
FROM orders o INNER JOIN order_items oi ON o.id = oi.order_id INNER JOIN products p ON oi.product_id = p.id
WHERE o.member_id = 1 AND o.deleted_at IS NULL AND o.status = 'CONFIRMED' AND p.deleted_at IS NULL
ORDER BY o.created_at DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

DROP INDEX idx_orders_member_created ON orders;
DROP INDEX idx_order_items_order_product ON order_items;

SHOW PROFILES;

-- =============================================================
-- [9] 전체 상품 좋아요 순 정렬 (브랜드 필터 없음)
-- =============================================================

-- 9-1. 인덱스 없음 / OFFSET 0
EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 9-2. 인덱스 없음 / OFFSET 500000
EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 500000;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 500000;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 9-3. 단일 인덱스 (like_count)
CREATE INDEX idx_products_like_count ON products(like_count DESC);

EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 500000;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 500000;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 9-4. 커버링 인덱스 (like_count + id + name + price + brand_id)
DROP INDEX idx_products_like_count ON products;
CREATE INDEX idx_products_like_covering ON products(like_count DESC, id, name, price, brand_id);

EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 500000;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 500000;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

DROP INDEX idx_products_like_covering ON products;

SHOW PROFILES;

-- =============================================================
-- [10] 복합 필터 — 브랜드 + 가격 범위 + 좋아요 순
-- 조건: brand_id = 1 AND price BETWEEN 50000 AND 500000 ORDER BY like_count DESC
-- 핵심 포인트: range 조건 이후 ORDER BY는 인덱스로 정렬 불가 → filesort 불가피
-- =============================================================

-- 10-1. 인덱스 없음
EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products
WHERE brand_id = 1 AND deleted_at IS NULL AND price BETWEEN 50000 AND 500000
ORDER BY like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products
WHERE brand_id = 1 AND deleted_at IS NULL AND price BETWEEN 50000 AND 500000
ORDER BY like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 10-2. 단일 인덱스 (brand_id)
CREATE INDEX idx_products_brand_id ON products(brand_id);

EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products
WHERE brand_id = 1 AND deleted_at IS NULL AND price BETWEEN 50000 AND 500000
ORDER BY like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products
WHERE brand_id = 1 AND deleted_at IS NULL AND price BETWEEN 50000 AND 500000
ORDER BY like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

DROP INDEX idx_products_brand_id ON products;

-- 10-3. 복합 인덱스 (brand_id, like_count DESC) — 정렬 최적화 우선
CREATE INDEX idx_products_brand_like ON products(brand_id, like_count DESC);

EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products
WHERE brand_id = 1 AND deleted_at IS NULL AND price BETWEEN 50000 AND 500000
ORDER BY like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products
WHERE brand_id = 1 AND deleted_at IS NULL AND price BETWEEN 50000 AND 500000
ORDER BY like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

DROP INDEX idx_products_brand_like ON products;

-- 10-4. 복합 인덱스 (brand_id, price) — range 필터 최적화 우선
CREATE INDEX idx_products_brand_price ON products(brand_id, price);

EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count
FROM products
WHERE brand_id = 1 AND deleted_at IS NULL AND price BETWEEN 50000 AND 500000
ORDER BY like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count
FROM products
WHERE brand_id = 1 AND deleted_at IS NULL AND price BETWEEN 50000 AND 500000
ORDER BY like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

DROP INDEX idx_products_brand_price ON products;

SHOW PROFILES;

-- =============================================================
-- [11] 재고 있는 상품만 — stocks JOIN + quantity > 0
-- 조건: brand_id = 1 AND s.quantity > 0 ORDER BY like_count DESC
-- =============================================================

-- 11-1. 인덱스 없음
EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, p.like_count, s.quantity
FROM products p INNER JOIN stocks s ON p.id = s.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL AND s.quantity > 0
ORDER BY p.like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, p.like_count, s.quantity
FROM products p INNER JOIN stocks s ON p.id = s.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL AND s.quantity > 0
ORDER BY p.like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 11-2. products: 단일 인덱스 (brand_id)
CREATE INDEX idx_products_brand_id ON products(brand_id);

EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, p.like_count, s.quantity
FROM products p INNER JOIN stocks s ON p.id = s.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL AND s.quantity > 0
ORDER BY p.like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, p.like_count, s.quantity
FROM products p INNER JOIN stocks s ON p.id = s.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL AND s.quantity > 0
ORDER BY p.like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 11-3. products: 복합 인덱스 (brand_id, like_count DESC)
DROP INDEX idx_products_brand_id ON products;
CREATE INDEX idx_products_brand_like ON products(brand_id, like_count DESC);

EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, p.like_count, s.quantity
FROM products p INNER JOIN stocks s ON p.id = s.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL AND s.quantity > 0
ORDER BY p.like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, p.like_count, s.quantity
FROM products p INNER JOIN stocks s ON p.id = s.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL AND s.quantity > 0
ORDER BY p.like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 11-4. + stocks: 인덱스 (product_id, quantity) 추가
CREATE INDEX idx_stocks_product_quantity ON stocks(product_id, quantity);

EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, p.like_count, s.quantity
FROM products p INNER JOIN stocks s ON p.id = s.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL AND s.quantity > 0
ORDER BY p.like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, p.like_count, s.quantity
FROM products p INNER JOIN stocks s ON p.id = s.product_id
WHERE p.brand_id = 1 AND p.deleted_at IS NULL AND s.quantity > 0
ORDER BY p.like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

DROP INDEX idx_products_brand_like ON products;
DROP INDEX idx_stocks_product_quantity ON stocks;

SHOW PROFILES;

-- =============================================================
-- [12] 내가 좋아요 누른 상품 — 좋아요 수 순 정렬
-- [7]과 동일한 JOIN이지만 ORDER BY p.like_count DESC (liked_at이 아님)
-- product_like_view 비교도 포함
-- =============================================================

-- 12-1. 인덱스 없음
EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, p.like_count
FROM likes l INNER JOIN products p ON l.product_id = p.id
WHERE l.member_id = 1 AND l.deleted_at IS NULL AND p.deleted_at IS NULL
ORDER BY p.like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, p.like_count
FROM likes l INNER JOIN products p ON l.product_id = p.id
WHERE l.member_id = 1 AND l.deleted_at IS NULL AND p.deleted_at IS NULL
ORDER BY p.like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 12-2. likes: 단일 인덱스 (member_id) — member 행 먼저 줄이기
CREATE INDEX idx_likes_member_id ON likes(member_id);

EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, p.like_count
FROM likes l INNER JOIN products p ON l.product_id = p.id
WHERE l.member_id = 1 AND l.deleted_at IS NULL AND p.deleted_at IS NULL
ORDER BY p.like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, p.like_count
FROM likes l INNER JOIN products p ON l.product_id = p.id
WHERE l.member_id = 1 AND l.deleted_at IS NULL AND p.deleted_at IS NULL
ORDER BY p.like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 12-3. + products: like_count 인덱스 추가
CREATE INDEX idx_products_like_count ON products(like_count DESC);

EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, p.like_count
FROM likes l INNER JOIN products p ON l.product_id = p.id
WHERE l.member_id = 1 AND l.deleted_at IS NULL AND p.deleted_at IS NULL
ORDER BY p.like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, p.like_count
FROM likes l INNER JOIN products p ON l.product_id = p.id
WHERE l.member_id = 1 AND l.deleted_at IS NULL AND p.deleted_at IS NULL
ORDER BY p.like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

DROP INDEX idx_likes_member_id ON likes;
DROP INDEX idx_products_like_count ON products;

-- 12-4. product_like_view JOIN (분리 테이블) + likes (member_id)
-- [5]에서 DROP TABLE 했으므로 재생성
CREATE TABLE product_like_view (
    product_id BIGINT NOT NULL PRIMARY KEY,
    like_count INT NOT NULL DEFAULT 0
);
INSERT INTO product_like_view (product_id, like_count)
SELECT id, like_count FROM products;

CREATE INDEX idx_likes_member_id ON likes(member_id);

EXPLAIN FORMAT=TRADITIONAL SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM likes l
  INNER JOIN products p ON l.product_id = p.id
  INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE l.member_id = 1 AND l.deleted_at IS NULL AND p.deleted_at IS NULL
ORDER BY plv.like_count DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT p.id, p.name, p.price, p.brand_id, plv.like_count
FROM likes l
  INNER JOIN products p ON l.product_id = p.id
  INNER JOIN product_like_view plv ON p.id = plv.product_id
WHERE l.member_id = 1 AND l.deleted_at IS NULL AND p.deleted_at IS NULL
ORDER BY plv.like_count DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

DROP INDEX idx_likes_member_id ON likes;
DROP TABLE product_like_view;

SHOW PROFILES;

-- =============================================================
-- [13] 전체 최신순 (브랜드 필터 없음)
-- ORDER BY created_at DESC — 신상품 피드 형태
-- =============================================================

-- 13-1. 인덱스 없음 / OFFSET 0
EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count, created_at
FROM products WHERE deleted_at IS NULL
ORDER BY created_at DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count, created_at
FROM products WHERE deleted_at IS NULL
ORDER BY created_at DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 13-2. 인덱스 없음 / OFFSET 500000
EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count, created_at
FROM products WHERE deleted_at IS NULL
ORDER BY created_at DESC LIMIT 20 OFFSET 500000;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count, created_at
FROM products WHERE deleted_at IS NULL
ORDER BY created_at DESC LIMIT 20 OFFSET 500000;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

-- 13-3. 단일 인덱스 (created_at DESC) / OFFSET 0, 500000
CREATE INDEX idx_products_created_at ON products(created_at DESC);

EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count, created_at
FROM products WHERE deleted_at IS NULL
ORDER BY created_at DESC LIMIT 20 OFFSET 0;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count, created_at
FROM products WHERE deleted_at IS NULL
ORDER BY created_at DESC LIMIT 20 OFFSET 0;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

EXPLAIN FORMAT=TRADITIONAL SELECT id, name, price, brand_id, like_count, created_at
FROM products WHERE deleted_at IS NULL
ORDER BY created_at DESC LIMIT 20 OFFSET 500000;

SET @t = NOW(6);
SELECT id, name, price, brand_id, like_count, created_at
FROM products WHERE deleted_at IS NULL
ORDER BY created_at DESC LIMIT 20 OFFSET 500000;
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @t, NOW(6)) / 1000, 2) AS 실행시간_ms;

DROP INDEX idx_products_created_at ON products;

SHOW PROFILES;
