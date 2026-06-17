0-- Volume 5 조회 성능 개선용 시드 스크립트
-- JPA(ddl-auto)가 생성한 brands/products 스키마/인덱스를 보존한 채 데이터만 채운다.
-- (테이블을 드롭하지 않으므로 status enum 타입과 products 인덱스 4개가 그대로 유지된다.)
-- 실행: docker exec -i docker-mysql-1 mysql --default-character-set=utf8mb4 -uapplication -papplication loopers < docs/volume-5-seed.sql
--       (--default-character-set=utf8mb4 누락 시 한글이 이중 인코딩되어 깨진다.)

SET SESSION cte_max_recursion_depth = 1000000;

-- 1) 브랜드 200건. TRUNCATE 로 auto_increment 를 리셋해 id 가 1..200 이 되도록 한다.
TRUNCATE TABLE brands;

INSERT INTO brands (name, description, created_at, updated_at, deleted_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 200
)
SELECT
    CONCAT('브랜드-', n),
    CONCAT('브랜드 설명 ', n),
    NOW(6),
    NOW(6),
    NULL
FROM seq;

-- 2) 상품 10만건. brand_id 는 위에서 만든 1..200 을 참조한다.
TRUNCATE TABLE products;

-- 각 컬럼을 다양하게 분포시킨다.
--  brand_id   : 곱셈 해시 % 200 + 1  -> 200개 브랜드에 분산(각 ~500건, 선택도 0.5%)
--  price      : 1,000 ~ 990,999 범위에 분산
--  status     : 10건 중 1건은 OFF_SALE (약 90% ON_SALE)
--  like_count : 곱셈 해시 % 10000 -> 0~9999 에 분산
--  created_at : 행마다 1초씩 과거로 -> 최신순 정렬에 변별력
INSERT INTO products (brand_id, name, description, price, status, like_count, created_at, updated_at, deleted_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100000
)
SELECT
    (n * 2246822519) % 200 + 1,
    CONCAT('상품-', n),
    CONCAT('상품 설명 ', n),
    (n * 37) % 990000 + 1000,
    CASE WHEN n % 10 = 0 THEN 'OFF_SALE' ELSE 'ON_SALE' END,
    (n * 2654435761) % 10000,
    NOW(6) - INTERVAL n SECOND,
    NOW(6),
    NULL
FROM seq;

ANALYZE TABLE brands;
ANALYZE TABLE products;

SELECT COUNT(*)                 AS total,
       SUM(status = 'ON_SALE')  AS on_sale,
       SUM(status = 'OFF_SALE') AS off_sale,
       COUNT(DISTINCT brand_id) AS brands,
       MIN(brand_id)            AS min_brand,
       MAX(brand_id)            AS max_brand,
       MIN(like_count)          AS min_like,
       MAX(like_count)          AS max_like
FROM products;
