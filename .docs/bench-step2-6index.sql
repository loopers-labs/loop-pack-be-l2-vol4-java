-- ============================================================
-- STEP 2: 6개 인덱스 — 필터 + 정렬
--
-- 실행 전 STEP 1 완료 필요
-- 실행 순서: Alt+X (Execute SQL Script)
-- ============================================================

ALTER TABLE products
    ADD INDEX idx_products_deleted_latest       (deleted_at, created_at DESC),
    ADD INDEX idx_products_deleted_likes        (deleted_at, like_count DESC),
    ADD INDEX idx_products_deleted_price        (deleted_at, price ASC),
    ADD INDEX idx_products_brand_deleted_latest (brand_id, deleted_at, created_at DESC),
    ADD INDEX idx_products_brand_deleted_likes  (brand_id, deleted_at, like_count DESC),
    ADD INDEX idx_products_brand_deleted_price  (brand_id, deleted_at, price ASC),
    DROP INDEX idx_products_deleted_at,
    DROP INDEX idx_products_brand_deleted;

SHOW INDEX FROM products;

SET @brand_id = (SELECT id FROM brands WHERE deleted_at IS NULL ORDER BY created_at LIMIT 1);

-- ============================================================
-- EXPLAIN 6개
-- ============================================================

-- ① 전체 + 최신순
EXPLAIN SELECT * FROM products WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20;

-- ② 전체 + 좋아요순
EXPLAIN SELECT * FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20;

-- ③ 전체 + 가격순
EXPLAIN SELECT * FROM products WHERE deleted_at IS NULL ORDER BY price ASC LIMIT 20;

-- ④ 브랜드 + 최신순
EXPLAIN SELECT * FROM products WHERE brand_id = @brand_id AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20;

-- ⑤ 브랜드 + 좋아요순
EXPLAIN SELECT * FROM products WHERE brand_id = @brand_id AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20;

-- ⑥ 브랜드 + 가격순
EXPLAIN SELECT * FROM products WHERE brand_id = @brand_id AND deleted_at IS NULL ORDER BY price ASC LIMIT 20;

-- ============================================================
-- optimizer_trace ① — LIMIT early termination 확인
--   → "index_order_summary": {"order_direction": "desc"} 등 확인
-- ============================================================

SET optimizer_trace = 'enabled=on';
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20;
SELECT * FROM information_schema.OPTIMIZER_TRACE;
SET optimizer_trace = 'enabled=off';

-- ============================================================
-- 벤치마크
-- ============================================================

CALL bench_products(10);
