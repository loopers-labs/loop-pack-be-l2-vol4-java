-- Stage 1 베이스라인 실측: EXPLAIN ANALYZE(S3·S4) + S1·S2 DNF 확인(>30s 캡).
-- 실행: docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < analyze-baseline.sql
SET @brand_id := 847;
SET @hot_product_id := 45577;

-- ===== S1·S2 (LIKES_DESC): 30초 캡으로 DNF 확인 =====
SET SESSION max_execution_time = 31000;

SELECT 'S2 attempt (brand filter)' AS marker;
SELECT p.id, p.price,
       (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) AS like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL AND p.brand_id = @brand_id
ORDER BY (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) DESC, p.id DESC
LIMIT 20 OFFSET 0;

SELECT 'S1 attempt (global)' AS marker;
SELECT p.id, p.price,
       (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) AS like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) DESC, p.id DESC
LIMIT 20 OFFSET 0;

SET SESSION max_execution_time = 0;

-- ===== S3·S4: 실측 가능 → EXPLAIN ANALYZE FORMAT=TREE =====
SELECT 'S3 ANALYZE (latest global)' AS marker;
EXPLAIN ANALYZE FORMAT=TREE
SELECT p.id, p.name, b.id, b.name, p.price, p.stock,
       (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) AS like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.created_at DESC, p.id DESC
LIMIT 20 OFFSET 0;

SELECT 'S4 ANALYZE (detail)' AS marker;
EXPLAIN ANALYZE FORMAT=TREE
SELECT p.id, p.name, p.description, b.id, b.name, p.price, p.stock,
       (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) AS like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.id = @hot_product_id AND p.deleted_at IS NULL;
