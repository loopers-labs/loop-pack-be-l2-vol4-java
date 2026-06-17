-- =============================================================================
-- 05. EXPLAIN (개선 후 — 복합 인덱스 적용)
-- =============================================================================
-- 03_explain_before.sql 과 완전히 동일한 쿼리를 다시 측정한다.
-- 기대: type=ref, "Backward index scan" 사용, filesort 제거, examined rows 급감.
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
