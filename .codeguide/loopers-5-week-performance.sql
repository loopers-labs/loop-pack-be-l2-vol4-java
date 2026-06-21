-- Round 5 local performance script.
-- Run on a disposable local MySQL schema. This script can remove catalog data.

-- 1. Optional local cleanup.
-- If foreign key data exists, clear dependent tables first or use a fresh schema.
-- TRUNCATE TABLE product_like;
-- TRUNCATE TABLE product;
-- TRUNCATE TABLE brand;

-- 2. Prepare 100 brands and 100,000 products with varied distribution.
CREATE TEMPORARY TABLE IF NOT EXISTS week5_digit (n INT PRIMARY KEY);
TRUNCATE TABLE week5_digit;
INSERT INTO week5_digit (n)
VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9);

CREATE TEMPORARY TABLE IF NOT EXISTS week5_seq_100000 (n INT PRIMARY KEY);
TRUNCATE TABLE week5_seq_100000;
INSERT INTO week5_seq_100000 (n)
SELECT ones.n
     + tens.n * 10
     + hundreds.n * 100
     + thousands.n * 1000
     + ten_thousands.n * 10000
     + 1 AS n
FROM week5_digit ones
CROSS JOIN week5_digit tens
CROSS JOIN week5_digit hundreds
CROSS JOIN week5_digit thousands
CROSS JOIN week5_digit ten_thousands
WHERE ones.n
    + tens.n * 10
    + hundreds.n * 100
    + thousands.n * 1000
    + ten_thousands.n * 10000 < 100000;

INSERT INTO brand (name, description, created_at, updated_at)
SELECT CONCAT('Brand ', n),
       CONCAT('Round 5 performance brand ', n),
       UTC_TIMESTAMP(6),
       UTC_TIMESTAMP(6)
FROM week5_seq_100000
WHERE n <= 100;

INSERT INTO product (
    brand_id,
    name,
    description,
    price,
    stock_quantity,
    like_count,
    status,
    created_at,
    updated_at
)
SELECT ((n - 1) % 100) + 1,
       CONCAT('Product ', n),
       CONCAT('Round 5 performance product ', n),
       1000 + (n % 100000),
       n % 500,
       (n * 37) % 20000,
       CASE WHEN n % 20 = 0 THEN 'SOLD_OUT' ELSE 'ON_SALE' END,
       TIMESTAMPADD(SECOND, -n, UTC_TIMESTAMP(6)),
       UTC_TIMESTAMP(6)
FROM week5_seq_100000;

-- 3. AS-IS query shape.
-- Run this before creating the TO-BE indexes if you need a strict before/after comparison.
EXPLAIN ANALYZE
SELECT p.*
FROM product p
WHERE p.status = 'ON_SALE'
  AND p.brand_id = 42
ORDER BY p.like_count DESC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT COUNT(*)
FROM product p
WHERE p.status = 'ON_SALE'
  AND p.brand_id = 42;

-- 4. TO-BE indexes. Hibernate local/test DDL also creates these from ProductJpaEntity.
CREATE INDEX idx_product_status_brand_like ON product (status, brand_id, like_count);
CREATE INDEX idx_product_status_like ON product (status, like_count);
CREATE INDEX idx_product_status_brand_created ON product (status, brand_id, created_at);
CREATE INDEX idx_product_status_brand_price ON product (status, brand_id, price);

-- 5. TO-BE explain checks.
SHOW INDEX FROM product
WHERE Key_name IN (
    'idx_product_status_brand_like',
    'idx_product_status_like',
    'idx_product_status_brand_created',
    'idx_product_status_brand_price'
);

EXPLAIN ANALYZE
SELECT p.*
FROM product p
WHERE p.status = 'ON_SALE'
  AND p.brand_id = 42
ORDER BY p.like_count DESC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT p.*
FROM product p
WHERE p.status = 'ON_SALE'
ORDER BY p.like_count DESC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT COUNT(*)
FROM product p
WHERE p.status = 'ON_SALE'
  AND p.brand_id = 42;
