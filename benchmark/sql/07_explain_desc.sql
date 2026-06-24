-- =============================================================================
-- 07. EXPLAIN (DESC 인덱스 + forward scan)
-- =============================================================================
-- 05_explain_after.sql 와 동일 쿼리. 기대: 플랜에서 "(reverse)" 가 사라지고 forward
-- index scan 으로 바뀜. filesort 제거 효과는 ASC 와 동일.
-- 타이밍은 sub-ms 라 노이즈가 크므로 ANALYZE 를 3회씩 반복해 비교한다.
-- =============================================================================

USE loopers_bench;

SELECT '=== A) 브랜드 필터 + 좋아요순 — FORMAT=TREE (DESC index) ===' AS '';
EXPLAIN FORMAT=TREE
SELECT * FROM product
WHERE brand_id = 1 AND deleted_at IS NULL
ORDER BY likes_count DESC, id DESC
LIMIT 20 OFFSET 0;

SELECT '=== A) ANALYZE x3 ===' AS '';
EXPLAIN ANALYZE SELECT * FROM product WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT * FROM product WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT * FROM product WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;

SELECT '=== B) 전체 + 좋아요순 — FORMAT=TREE (DESC index) ===' AS '';
EXPLAIN FORMAT=TREE
SELECT * FROM product
WHERE deleted_at IS NULL
ORDER BY likes_count DESC, id DESC
LIMIT 20 OFFSET 0;

SELECT '=== B) ANALYZE x3 ===' AS '';
EXPLAIN ANALYZE SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;

SELECT '=== A-deep) offset 1000 — FORMAT=TREE (DESC index) ===' AS '';
EXPLAIN FORMAT=TREE
SELECT * FROM product
WHERE brand_id = 1 AND deleted_at IS NULL
ORDER BY likes_count DESC, id DESC
LIMIT 20 OFFSET 1000;

SELECT '=== A-deep) ANALYZE x3 ===' AS '';
EXPLAIN ANALYZE SELECT * FROM product WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 1000;
EXPLAIN ANALYZE SELECT * FROM product WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 1000;
EXPLAIN ANALYZE SELECT * FROM product WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 1000;
