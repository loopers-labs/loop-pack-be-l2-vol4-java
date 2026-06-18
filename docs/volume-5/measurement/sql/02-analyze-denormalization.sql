-- Stage 2 비정규화 실측: S1~S4 EXPLAIN ANALYZE FORMAT=TREE.
-- 서브쿼리 제거로 베이스라인(01-analyze-baseline.sql)의 S1·S2 DNF 가 해소되는지 확인.
-- 혹시 모를 장기 실행 대비 30초 캡(max_execution_time)을 유지한다 — 캡에 걸리면 DNF.
-- 실행: docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < 02-analyze-denormalization.sql
SET @brand_id := 847;
SET @hot_product_id := 45577;
SET SESSION max_execution_time = 31000;

SELECT 'S1 ANALYZE (likes global)' AS marker;
EXPLAIN ANALYZE FORMAT=TREE
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.like_count DESC, p.id DESC
LIMIT 20 OFFSET 0;

SELECT 'S2 ANALYZE (likes brand)' AS marker;
EXPLAIN ANALYZE FORMAT=TREE
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL AND p.brand_id = @brand_id
ORDER BY p.like_count DESC, p.id DESC
LIMIT 20 OFFSET 0;

SELECT 'S3 ANALYZE (latest global)' AS marker;
EXPLAIN ANALYZE FORMAT=TREE
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.created_at DESC, p.id DESC
LIMIT 20 OFFSET 0;

SELECT 'S4 ANALYZE (detail)' AS marker;
EXPLAIN ANALYZE FORMAT=TREE
SELECT p.id, p.name, p.description, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.id = @hot_product_id AND p.deleted_at IS NULL;

SET SESSION max_execution_time = 0;