SET SESSION cte_max_recursion_depth = 1000000;

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE product_like;
TRUNCATE TABLE product;
TRUNCATE TABLE brand;
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO brand (name, description, created_at, updated_at, deleted_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100
)
SELECT
    CONCAT('Brand ', n),
    CONCAT('Benchmark brand ', n),
    NOW(6),
    NOW(6),
    NULL
FROM seq;

INSERT INTO product (brand_id, name, description, price, stock, like_count, created_at, updated_at, deleted_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100000
)
SELECT
    ((n - 1) % 100) + 1,
    CONCAT('Product ', n),
    CONCAT('Benchmark product ', n),
    1000 + ((n * 37) % 200000),
    ((n * 11) % 1000),
    ((n * 17) % 10000),
    TIMESTAMPADD(SECOND, -n, NOW(6)),
    NOW(6),
    NULL
FROM seq;

ANALYZE TABLE product;

-- AS-IS 측정: 인덱스 적용 전 상태를 보려면 아래 인덱스를 제거한 뒤 실행한다.
-- ALTER TABLE product DROP INDEX idx_product_brand_like_count;
-- ALTER TABLE product DROP INDEX idx_product_brand_price;
-- ALTER TABLE product DROP INDEX idx_product_brand_created_at;

EXPLAIN
SELECT *
FROM product
WHERE brand_id = 42
  AND deleted_at IS NULL
ORDER BY like_count DESC
LIMIT 20 OFFSET 0;

EXPLAIN
SELECT *
FROM product
WHERE brand_id = 42
  AND deleted_at IS NULL
ORDER BY price ASC
LIMIT 20 OFFSET 0;

EXPLAIN
SELECT *
FROM product
WHERE brand_id = 42
  AND deleted_at IS NULL
ORDER BY created_at DESC
LIMIT 20 OFFSET 0;

-- TO-BE 측정: JPA schema generation을 쓰지 않는 DB에서 직접 비교할 때 사용한다.
-- CREATE INDEX idx_product_brand_like_count ON product (brand_id, like_count);
-- CREATE INDEX idx_product_brand_price ON product (brand_id, price);
-- CREATE INDEX idx_product_brand_created_at ON product (brand_id, created_at);
