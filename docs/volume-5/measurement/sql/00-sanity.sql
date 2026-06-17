-- 데이터 분포 sanity 체크. 적재 직후 1회 실행해 분포를 확인하고,
-- 인기/희귀 브랜드 id 와 핫 상품 id 를 골라 EXPLAIN/k6 시나리오 파라미터로 사용한다.
-- 실행: docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < 00-sanity.sql

-- 1) 전체 건수
SELECT 'brands' AS t, COUNT(*) AS cnt FROM brands
UNION ALL SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'likes', COUNT(*) FROM likes;

-- 2) 브랜드별 상품 수 분포 (편차 확인)
SELECT MIN(c) AS min_products, MAX(c) AS max_products, ROUND(AVG(c), 1) AS avg_products, ROUND(STDDEV(c), 1) AS stddev
FROM (SELECT brand_id, COUNT(*) AS c FROM products GROUP BY brand_id) t;

-- 3) 인기 브랜드 Top 5 (S2 의 brandId 후보 — Full Scan 분기 관찰용)
SELECT brand_id, COUNT(*) AS product_count FROM products GROUP BY brand_id ORDER BY product_count DESC LIMIT 5;

-- 4) 희귀 브랜드 Bottom 5
SELECT brand_id, COUNT(*) AS product_count FROM products GROUP BY brand_id ORDER BY product_count ASC LIMIT 5;

-- 5) 상품별 좋아요 수 분포 (파워법칙 확인)
SELECT MIN(c) AS min_likes, MAX(c) AS max_likes, ROUND(AVG(c), 1) AS avg_likes
FROM (SELECT product_id, COUNT(*) AS c FROM likes GROUP BY product_id) t;

-- 6) 상품별 좋아요 히스토그램
SELECT
  CASE
    WHEN c < 50 THEN 'a. 0-49'
    WHEN c < 100 THEN 'b. 50-99'
    WHEN c < 500 THEN 'c. 100-499'
    WHEN c < 1000 THEN 'd. 500-999'
    ELSE 'e. 1000+'
  END AS bucket,
  COUNT(*) AS product_count
FROM (SELECT product_id, COUNT(*) AS c FROM likes GROUP BY product_id) t
GROUP BY bucket ORDER BY bucket;

-- 7) 좋아요 Top 5 상품 (S4 상세/캐시 핫 상품 후보)
SELECT product_id, COUNT(*) AS like_count FROM likes GROUP BY product_id ORDER BY like_count DESC LIMIT 5;
