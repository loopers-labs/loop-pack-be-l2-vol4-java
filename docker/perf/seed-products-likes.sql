-- =====================================================================
-- week5 성능 측정용 시드: 상품 10만 + 좋아요 ~300만(파레토) + 브랜드 50
-- 인덱스 없는 baseline 측정 전제. 재실행 가능(맨 위에서 truncate).
-- 실행: docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < 이 파일
-- =====================================================================

SET SESSION unique_checks = 0;       -- 생성 규칙상 (user_id, product_id) 유일 보장 → 검증 비용 절감
SET SESSION foreign_key_checks = 0;

-- 0..99999 숫자 헬퍼 (자릿수 cross join, 재귀 CTE 깊이 제한 회피)
DROP TABLE IF EXISTS seq;
CREATE TABLE seq (n INT NOT NULL PRIMARY KEY);
INSERT INTO seq (n)
SELECT d5.d*10000 + d4.d*1000 + d3.d*100 + d2.d*10 + d1.d AS n
FROM        (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d1
CROSS JOIN  (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d2
CROSS JOIN  (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d3
CROSS JOIN  (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d4
CROSS JOIN  (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d5;

TRUNCATE TABLE likes;
TRUNCATE TABLE product_stocks;
TRUNCATE TABLE products;
TRUNCATE TABLE brands;

-- 브랜드 50개 (id 1..50)
INSERT INTO brands (name, description, logo_url, created_at, updated_at)
SELECT CONCAT('브랜드-', s.n), CONCAT('브랜드 설명 ', s.n), NULL, NOW(6), NOW(6)
FROM seq s WHERE s.n BETWEEN 1 AND 50;

-- 상품 10만개
--  브랜드 분포(편향): brand1 = 50%(mega), brand2~10 = 30%(medium), brand11~50 = 20%(small tail)
--  가격/생성일시 다양, 2% 는 SUSPENDED 로 status 필터가 실제로 작동하게
INSERT INTO products (brand_id, name, description, price, status, thumbnail_url, created_at, updated_at)
SELECT
    CASE WHEN s.n % 10 < 5 THEN 1
         WHEN s.n % 10 < 8 THEN 2 + (s.n % 9)
         ELSE 11 + (s.n % 40) END                         AS brand_id,
    CONCAT('상품-', s.n)                                   AS name,
    CONCAT('상품 설명 ', s.n)                              AS description,
    100 + (s.n * 131 % 1000000)                            AS price,
    CASE WHEN s.n % 50 = 0 THEN 'SUSPENDED' ELSE 'ON_SALE' END AS status,
    NULL                                                   AS thumbnail_url,
    NOW(6) - INTERVAL s.n SECOND                           AS created_at,
    NOW(6)                                                 AS updated_at
FROM seq s;   -- s.n 0..99999

-- 재고 1:1 (수량 0~499 다양, 일부 품절)
INSERT INTO product_stocks (product_id, quantity, created_at, updated_at)
SELECT p.id, (p.id % 500), NOW(6), NOW(6) FROM products p;

-- 좋아요 ~300만 (파레토)
--  상위 10%(id%10=0) = 상품당 200~279개, 나머지 = 1~13개
--  user_id 는 상품 내에서 1..K 로 부여 → (user_id, product_id) 자연 유일
--  판매중 상품만 좋아요 부여
INSERT INTO likes (product_id, user_id, created_at, updated_at)
SELECT p.id, s.n + 1, NOW(6), NOW(6)
FROM products p
JOIN seq s ON s.n < (CASE WHEN p.id % 10 = 0 THEN 200 + (p.id % 80)
                          ELSE 1 + (p.id % 13) END)
WHERE p.status = 'ON_SALE';

DROP TABLE seq;
SET SESSION unique_checks = 1;
SET SESSION foreign_key_checks = 1;

-- 결과 요약
SELECT (SELECT COUNT(*) FROM brands)         AS brands,
       (SELECT COUNT(*) FROM products)       AS products,
       (SELECT COUNT(*) FROM product_stocks) AS stocks,
       (SELECT COUNT(*) FROM likes)          AS likes;
