-- 5주차 읽기 성능 과제용 시딩 스크립트
-- 브랜드 50개 + 상품 10만건. 재귀 CTE로 일괄 삽입.
-- 실행: docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < docs/perf/seed.sql

SET SESSION cte_max_recursion_depth = 200000;

-- 멱등 실행을 위해 기존 데이터 정리
DELETE FROM products;
DELETE FROM brands;
ALTER TABLE products AUTO_INCREMENT = 1;
ALTER TABLE brands AUTO_INCREMENT = 1;

-- 브랜드 50개 (id 1~50)
INSERT INTO brands (name, created_at, updated_at, deleted_at)
WITH RECURSIVE seq AS (
  SELECT 1 AS n
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 50
)
SELECT CONCAT('브랜드-', n), NOW(), NOW(), NULL
FROM seq;

-- 상품 10만건
--  brand_id   : 1~50 균등 (필터 카디널리티 중간)
--  price      : 1,000 ~ 1,000,000 균등
--  like_count : POW(RAND(), 8) * 10000 → 멱법칙. 대부분 0~수십, 극소수만 수천 (인기 상품 모사)
--  created_at : 최근 365일 내 임의 시각 (latest 정렬/기간 필터용)
INSERT INTO products (brand_id, name, description, price, like_count, created_at, updated_at, deleted_at)
WITH RECURSIVE seq AS (
  SELECT 1 AS n
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 100000
)
SELECT
  FLOOR(1 + RAND() * 50),
  CONCAT('상품-', n),
  CONCAT('상품 설명 ', n),
  FLOOR(1000 + RAND() * 999000),
  FLOOR(POW(RAND(), 8) * 10000),
  NOW() - INTERVAL FLOOR(RAND() * 365) DAY,
  NOW(),
  NULL
FROM seq;
