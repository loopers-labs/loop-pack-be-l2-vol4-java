-- =====================================================
-- Performance Test Seed Data
-- brand: 1,000 | product: 1,000,000 | inventory: 1,000,000
-- users / likes: 제외
-- =====================================================

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE likes;
TRUNCATE TABLE inventory;
TRUNCATE TABLE product;
TRUNCATE TABLE users;
TRUNCATE TABLE brand;
SET FOREIGN_KEY_CHECKS = 1;

SET @now = NOW();

-- =====================================================
-- 1. BRAND (1,000개)
-- =====================================================
INSERT INTO brand (name, description, created_at, updated_at)
SELECT
    CONCAT('Brand_', seq),
    CONCAT('Description for Brand_', seq),
    @now, @now
FROM (
    SELECT a.n * 100 + b.n * 10 + c.n + 1 AS seq
    FROM
        (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a,
        (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b,
        (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c
    WHERE a.n * 100 + b.n * 10 + c.n + 1 <= 1000
) nums;

-- =====================================================
-- 2. PRODUCT (1,000,000개)
-- =====================================================
INSERT INTO product (brand_id, name, description, price, like_count, created_at, updated_at)
SELECT
    MOD(seq - 1, 1000) + 1,
    CONCAT('Product_', seq),
    CONCAT('Description for Product_', seq),
    MOD(seq * 9871 + seq * seq * 13, 990001) + 10000,
    MOD(seq * 7919 + seq * seq * 31, 100001),
    @now, @now
FROM (
    SELECT a.n * 100000 + b.n * 10000 + c.n * 1000 + d.n * 100 + e.n * 10 + f.n + 1 AS seq
    FROM
        (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a,
        (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b,
        (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c,
        (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d,
        (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) e,
        (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) f
    WHERE a.n * 100000 + b.n * 10000 + c.n * 1000 + d.n * 100 + e.n * 10 + f.n + 1 <= 1000000
) nums;

-- =====================================================
-- 3. INVENTORY (1,000,000개 — product와 1:1)
-- =====================================================
INSERT INTO inventory (product_id, quantity, created_at, updated_at)
SELECT
    id,
    MOD(id * 7 + id * id * 3, 1000) + 1,
    @now, @now
FROM product;

-- =====================================================
-- 검증
-- =====================================================
SELECT 'brand'      AS `table`, COUNT(*) AS count FROM brand
UNION ALL
SELECT 'product',   COUNT(*) FROM product
UNION ALL
SELECT 'inventory', COUNT(*) FROM inventory;
