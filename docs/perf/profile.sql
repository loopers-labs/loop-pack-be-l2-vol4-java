-- 인덱스 on/off 실행 시간 비교 (단건)
-- 실행: docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < docs/perf/profile.sql
SET profiling = 1;
SELECT * FROM products IGNORE INDEX (idx_products_brand_like) WHERE brand_id = 16 ORDER BY like_count DESC LIMIT 20;
SELECT * FROM products WHERE brand_id = 16 ORDER BY like_count DESC LIMIT 20;
SHOW PROFILES;
