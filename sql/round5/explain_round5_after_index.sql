-- =====================================================================
-- Round 5 — TO-BE (인덱스 추가 후) 측정. before 와 동일 쿼리/조건으로 비교.
-- 기대: type ALL→ref, key=idx_..., Extra 의 Using filesort 소멸
-- =====================================================================

-- ① latest
EXPLAIN
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL AND p.brand_id = 7
ORDER BY p.created_at DESC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL AND p.brand_id = 7
ORDER BY p.created_at DESC
LIMIT 20 OFFSET 0;

-- ② price_asc
EXPLAIN
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL AND p.brand_id = 7
ORDER BY p.price ASC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL AND p.brand_id = 7
ORDER BY p.price ASC
LIMIT 20 OFFSET 0;
