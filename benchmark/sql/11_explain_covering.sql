-- =============================================================================
-- 11. EXPLAIN (커버링 인덱스 효과 측정)
-- =============================================================================
-- 2개 축으로 비교한다:
--   축1) SELECT 컬럼: SELECT *  vs  SELECT 목록컬럼(id, brand_id, name, price, likes_count)
--   축2) 인덱스      : 비커버링(idx_*_likes_desc)  vs  커버링(idx_*_cover)  — FORCE INDEX 로 강제
-- 기대:
--   - SELECT * 는 어떤 인덱스를 써도 커버링 불가(description/image_url 미포함) → PK 룩업 잔존
--   - SELECT 목록컬럼 + 커버링 인덱스 = "Using index" (테이블 룩업 제거)
--   - SELECT 목록컬럼 + 비커버링 인덱스 = name/price 위해 PK 룩업 잔존
-- 타이밍은 sub-ms 노이즈가 크므로 ANALYZE 3회 반복. 딥오프셋(1000)에서 차이가 더 잘 보인다.
-- =============================================================================

USE loopers_bench;

-- -----------------------------------------------------------------------------
-- A) 브랜드 필터 + 좋아요순
-- -----------------------------------------------------------------------------

SELECT '=== A1) SELECT * + 비커버링 (PK 룩업 잔존 예상) — TREE ===' AS '';
EXPLAIN FORMAT=TREE
SELECT * FROM product FORCE INDEX (idx_brand_active_likes_desc)
WHERE brand_id = 1 AND deleted_at IS NULL
ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;

SELECT '=== A2) SELECT 목록컬럼 + 비커버링 (PK 룩업 잔존 예상) — TREE ===' AS '';
EXPLAIN FORMAT=TREE
SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_brand_active_likes_desc)
WHERE brand_id = 1 AND deleted_at IS NULL
ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;

SELECT '=== A3) SELECT 목록컬럼 + 커버링 (Using index 기대) — TREE ===' AS '';
EXPLAIN FORMAT=TREE
SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_brand_cover)
WHERE brand_id = 1 AND deleted_at IS NULL
ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;

SELECT '=== A4) SELECT * + 커버링 강제 (여전히 PK 룩업 — 커버링 무의미) — TREE ===' AS '';
EXPLAIN FORMAT=TREE
SELECT * FROM product FORCE INDEX (idx_brand_cover)
WHERE brand_id = 1 AND deleted_at IS NULL
ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;

-- deep offset 1000 — 룩업 비용이 누적돼 차이가 더 크게 드러남
SELECT '=== A-deep) 목록컬럼, offset 1000 — 비커버링 ANALYZE x3 ===' AS '';
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_brand_active_likes_desc) WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 1000;
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_brand_active_likes_desc) WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 1000;
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_brand_active_likes_desc) WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 1000;

SELECT '=== A-deep) 목록컬럼, offset 1000 — 커버링 ANALYZE x3 ===' AS '';
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_brand_cover) WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 1000;
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_brand_cover) WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 1000;
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_brand_cover) WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 1000;

-- page 0 도 함께 (얕은 페이지에서의 차이 — 보통 노이즈 수준)
SELECT '=== A-page0) 목록컬럼 — 비커버링 ANALYZE x3 ===' AS '';
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_brand_active_likes_desc) WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_brand_active_likes_desc) WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_brand_active_likes_desc) WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;

SELECT '=== A-page0) 목록컬럼 — 커버링 ANALYZE x3 ===' AS '';
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_brand_cover) WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_brand_cover) WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_brand_cover) WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 0;

-- -----------------------------------------------------------------------------
-- B) 전체 + 좋아요순 (deep offset 1000)
-- -----------------------------------------------------------------------------
SELECT '=== B-deep) 목록컬럼, offset 1000 — 비커버링 ANALYZE x3 ===' AS '';
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_active_likes_desc) WHERE deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 1000;
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_active_likes_desc) WHERE deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 1000;
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_active_likes_desc) WHERE deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 1000;

SELECT '=== B-deep) 목록컬럼, offset 1000 — 커버링 ANALYZE x3 ===' AS '';
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_active_cover) WHERE deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 1000;
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_active_cover) WHERE deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 1000;
EXPLAIN ANALYZE SELECT id, brand_id, name, price, likes_count FROM product FORCE INDEX (idx_active_cover) WHERE deleted_at IS NULL ORDER BY likes_count DESC, id DESC LIMIT 20 OFFSET 1000;
