-- =====================================================================
-- brandId 필터 유무에 따른 likes_desc 성능 비교
-- =====================================================================
-- 현재 JPQL: AND (:brandId IS NULL OR p.brandId = :brandId)
--
-- 이 패턴의 문제:
--   - 옵티마이저는 쿼리 플래닝 시점에 파라미터 값을 모른다.
--   - OR 조건이 있으면 인덱스 선택이 불안정해진다.
--   - brandId=null 케이스에서 idx_products_likecount를 타야 하는데
--     풀스캔을 선택할 수 있다.
--
-- 실행 방법:
--   docker exec -i <mysql_container> mysql -uapplication -papplication loopers \
--     < sql/round5/brand_filter_compare.sql
-- =====================================================================

-- 현재 인덱스 확인
SHOW INDEX FROM products WHERE Key_name LIKE '%like%' OR Key_name LIKE '%brand%';

-- =====================================================================
-- CASE A: brandId 필터 O  (brandId = 7)
-- 기대: idx_products_brand_likecount 사용, rows ~ 수백
-- =====================================================================
EXPLAIN
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL
  AND (7 IS NULL OR p.brand_id = 7)
ORDER BY p.like_count DESC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL
  AND (7 IS NULL OR p.brand_id = 7)
ORDER BY p.like_count DESC
LIMIT 20 OFFSET 0;

-- =====================================================================
-- CASE B: brandId 필터 X  (brandId = null → 조건 항상 TRUE)
-- 기대: idx_products_likecount 사용, rows ~ 20 (LIMIT early stop)
-- 실제: 옵티마이저가 OR 조건에 막혀 풀스캔 선택할 수 있음
-- =====================================================================
EXPLAIN
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL
  AND (NULL IS NULL OR p.brand_id = NULL)
ORDER BY p.like_count DESC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL
  AND (NULL IS NULL OR p.brand_id = NULL)
ORDER BY p.like_count DESC
LIMIT 20 OFFSET 0;

-- =====================================================================
-- CASE C: 쿼리 분리 후 brandId 없는 경로 (개선안)
-- 기대: idx_products_likecount 확실히 사용, filesort 없음
-- =====================================================================
EXPLAIN
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL
ORDER BY p.like_count DESC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL
ORDER BY p.like_count DESC
LIMIT 20 OFFSET 0;

-- =====================================================================
-- CASE D: 쿼리 분리 후 brandId 있는 경로 (개선안)
-- 기대: idx_products_brand_likecount 확실히 사용
-- =====================================================================
EXPLAIN
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL
  AND p.brand_id = 7
ORDER BY p.like_count DESC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL
  AND p.brand_id = 7
ORDER BY p.like_count DESC
LIMIT 20 OFFSET 0;

-- =====================================================================
-- 결과 비교 예상표
-- =====================================================================
-- CASE   | type  | key                          | rows   | Extra
-- -------+-------+------------------------------+--------+------------------
-- A (OR) | range | idx_products_brand_likecount | ~수백  | (없음 — 정상)
-- B (OR) | ALL   | NULL                         | ~10만  | Using filesort  ← 문제
-- C (분리) | index | idx_products_likecount      | 20     | Using index
-- D (분리) | range | idx_products_brand_likecount| ~수백  | (없음 — 정상)
-- =====================================================================
