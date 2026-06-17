-- =============================================================================
-- 03. EXPLAIN (개선 전 — 보조 인덱스 없음)
-- =============================================================================
-- 운영 코드(ProductRepositoryImpl.findActivePage)가 생성하는 두 쿼리를 그대로 측정한다.
--   A) 브랜드 필터 + 좋아요순 : findByBrandIdAndDeletedAtIsNull(brandId, pageable[likesCount DESC, id DESC])
--   B) 전체       + 좋아요순 : findByDeletedAtIsNull(pageable[likesCount DESC, id DESC])
--
-- EXPLAIN FORMAT=TREE  : 실행 계획(스캔 타입/filesort 여부)
-- EXPLAIN ANALYZE      : 실제 실행 시간(actual time) — MySQL 8.0.18+
--
-- ※ brand_id = 1 은 02_seed 에서 가장 상품이 많은 브랜드(POW 편중의 최빈값).
-- =============================================================================

USE loopers_bench;

SELECT '=== A) 브랜드 필터 + 좋아요순 (page 0) — FORMAT=TREE ===' AS '';
EXPLAIN FORMAT=TREE
SELECT * FROM product
WHERE brand_id = 1 AND deleted_at IS NULL
ORDER BY likes_count DESC, id DESC
LIMIT 20 OFFSET 0;

SELECT '=== A) 브랜드 필터 + 좋아요순 (page 0) — ANALYZE ===' AS '';
EXPLAIN ANALYZE
SELECT * FROM product
WHERE brand_id = 1 AND deleted_at IS NULL
ORDER BY likes_count DESC, id DESC
LIMIT 20 OFFSET 0;

SELECT '=== B) 전체 + 좋아요순 (page 0) — FORMAT=TREE ===' AS '';
EXPLAIN FORMAT=TREE
SELECT * FROM product
WHERE deleted_at IS NULL
ORDER BY likes_count DESC, id DESC
LIMIT 20 OFFSET 0;

SELECT '=== B) 전체 + 좋아요순 (page 0) — ANALYZE ===' AS '';
EXPLAIN ANALYZE
SELECT * FROM product
WHERE deleted_at IS NULL
ORDER BY likes_count DESC, id DESC
LIMIT 20 OFFSET 0;

SELECT '=== A-deep) 브랜드 필터 + 좋아요순 (deep offset 1000) — ANALYZE ===' AS '';
EXPLAIN ANALYZE
SELECT * FROM product
WHERE brand_id = 1 AND deleted_at IS NULL
ORDER BY likes_count DESC, id DESC
LIMIT 20 OFFSET 1000;
