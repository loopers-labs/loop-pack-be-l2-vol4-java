-- =============================================================================
-- 09. EXPLAIN (단일 컬럼 인덱스)
-- =============================================================================
-- 03/05/07 과 동일 쿼리. 관전 포인트:
--   - 옵티마이저가 brand_id / likes_count / deleted_at 중 무엇을 고르는가
--   - "Sort"(filesort) 노드가 남는가
--   - examined rows (LIMIT 20 을 채우려 몇 행을 읽는가)
-- =============================================================================

USE loopers_bench;

SELECT '=== A) 브랜드 필터 + 좋아요순 — FORMAT=TREE (single) ===' AS '';
EXPLAIN FORMAT=TREE
SELECT * FROM product
WHERE brand_id = 1 AND deleted_at IS NULL
ORDER BY likes_count DESC, id DESC
LIMIT 20 OFFSET 0;

SELECT '=== A) ANALYZE x3 ===' AS '';
EXPLAIN ANALYZE SELECT * FROM product WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT * FROM product WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT * FROM product WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;

SELECT '=== B) 전체 + 좋아요순 — FORMAT=TREE (single) ===' AS '';
EXPLAIN FORMAT=TREE
SELECT * FROM product
WHERE deleted_at IS NULL
ORDER BY likes_count DESC, id DESC
LIMIT 20 OFFSET 0;

SELECT '=== B) ANALYZE x3 ===' AS '';
EXPLAIN ANALYZE SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;
