-- ============================================================
-- STEP 1: 2개 인덱스 — (deleted_at), (brand_id, deleted_at)
--
-- 실행 전 STEP 0 완료 필요
-- 실행 순서: Alt+X (Execute SQL Script)
-- ============================================================

ALTER TABLE products
    ADD INDEX idx_products_brand_deleted (brand_id, deleted_at),
    ADD INDEX idx_products_deleted_at (deleted_at),
    DROP INDEX idx_temp_brand;

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
-- optimizer_trace ① — ref vs ALL 비용 비교 핵심
--   → considered_access_paths 에서 ref cost < ALL cost 확인
-- ============================================================

SET optimizer_trace = 'enabled=on';
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20;
SELECT * FROM information_schema.OPTIMIZER_TRACE;
SET optimizer_trace = 'enabled=off';

-- ============================================================
-- 벤치마크
-- ============================================================

CALL bench_products(10);
