-- ============================================================
-- STEP 0: 커스텀 인덱스 없음 (FK 인덱스만)
--
-- 실행 순서: Alt+X (Execute SQL Script)
-- ============================================================

-- STEP 2에서 돌아올 때만 아래 주석 해제 후 실행
-- (6개 인덱스 → 0개로 전환)
-- ALTER TABLE products
--     ADD INDEX idx_temp_brand (brand_id),
--     DROP INDEX idx_products_deleted_latest,
--     DROP INDEX idx_products_deleted_likes,
--     DROP INDEX idx_products_deleted_price,
--     DROP INDEX idx_products_brand_deleted_latest,
--     DROP INDEX idx_products_brand_deleted_likes,
--     DROP INDEX idx_products_brand_deleted_price;

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
-- optimizer_trace ① (인덱스 없으므로 ALL만 존재 — baseline)
-- ============================================================

SET optimizer_trace = 'enabled=on';
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20;
SELECT * FROM information_schema.OPTIMIZER_TRACE;
SET optimizer_trace = 'enabled=off';

-- ============================================================
-- 벤치마크
-- ============================================================

CALL bench_products(10);
