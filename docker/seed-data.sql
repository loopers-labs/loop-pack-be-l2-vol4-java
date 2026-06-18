-- =====================================================
-- Performance Test Seed Data
-- brand    :     1,000
-- product  : 1,000,000 (hotspot 상품 10개 like_count 집중)
-- inventory: 1,000,000
-- users    :    10,000 (공통 비밀번호: Test1234!)
-- likes    :    ~46,000 (hotspot 상품 중심)
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
-- 4. PRODUCT like_count 계층형 분포 (Power-law)
--
--  Tier 1 (    10개): 8,000 ~ 10,000  극단적 핫스팟
--  Tier 2 (    90개): 5,000 ~  8,000  강한 핫스팟
--  Tier 3 (   900개): 2,000 ~  5,000  중간 핫스팟
--  Tier 4 ( 9,000개):   500 ~  2,000  약한 핫스팟
--  Tier 5 (90,000개):   100 ~    500  일반 인기
--  나머지(900,000개): 기존 랜덤값 유지 (0 ~ 100,001)
-- =====================================================
UPDATE product SET like_count = FLOOR(8000 + RAND() * 2001)
WHERE id BETWEEN 1 AND 10;

UPDATE product SET like_count = FLOOR(5000 + RAND() * 3001)
WHERE id BETWEEN 11 AND 100;

UPDATE product SET like_count = FLOOR(2000 + RAND() * 3001)
WHERE id BETWEEN 101 AND 1000;

UPDATE product SET like_count = FLOOR(500 + RAND() * 1501)
WHERE id BETWEEN 1001 AND 10000;

UPDATE product SET like_count = FLOOR(100 + RAND() * 401)
WHERE id BETWEEN 10001 AND 100000;

-- =====================================================
-- 5. USERS (10,000명)
--    공통 비밀번호: Test1234!
--    BCrypt 해시 (strength=10): $2a$10$aU6bz61RObtOZ6J1ak/PSe9b7EtptVlbvKCBYKXtBy98IG1B6yiZG
-- =====================================================
SET @common_password = '$2a$10$aU6bz61RObtOZ6J1ak/PSe9b7EtptVlbvKCBYKXtBy98IG1B6yiZG';

INSERT INTO users (user_id, name, email, password, birth_date, created_at, updated_at)
SELECT
    CONCAT('seeduser_', seq),
    CONCAT('SeedUser_', seq),
    CONCAT('seeduser_', seq, '@test.com'),
    @common_password,
    '1990-01-01',
    @now, @now
FROM (
    SELECT a.n * 1000 + b.n * 100 + c.n * 10 + d.n + 1 AS seq
    FROM
        (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a,
        (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b,
        (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c,
        (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d
    WHERE a.n * 1000 + b.n * 100 + c.n * 10 + d.n + 1 <= 10000
) nums;

-- =====================================================
-- 6. LIKES (핫스팟 계층 반영, ~110,000건)
--    Tier 1 (id  1~10 ): seeduser_1 ~ seeduser_10000 (10,000건 × 10 = 100,000건)
--    Tier 2 (id 11~100): seeduser_1 ~ seeduser_1000  ( 1,000건 × 90 =  90,000건)
--    → 총 ~190,000건 (likes 테이블)
-- =====================================================

-- Tier 1 (극단적 핫스팟 id 1~10): 전체 10,000명이 각 상품에 좋아요
INSERT INTO likes (user_id, product_id, created_at, updated_at)
SELECT u.id, p.pid, @now, @now
FROM users u
JOIN (
    SELECT 1  AS pid UNION ALL SELECT 2  UNION ALL SELECT 3  UNION ALL SELECT 4  UNION ALL SELECT 5
    UNION ALL SELECT 6  UNION ALL SELECT 7  UNION ALL SELECT 8  UNION ALL SELECT 9  UNION ALL SELECT 10
) p
WHERE u.user_id LIKE 'seeduser_%';

-- Tier 2 (강한 핫스팟 id 11~100): 상위 1,000명이 각 상품에 좋아요
INSERT INTO likes (user_id, product_id, created_at, updated_at)
SELECT u.id, p.pid, @now, @now
FROM users u
JOIN (
    SELECT 11 AS pid UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
    UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
    UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24 UNION ALL SELECT 25
    UNION ALL SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30
    UNION ALL SELECT 31 UNION ALL SELECT 32 UNION ALL SELECT 33 UNION ALL SELECT 34 UNION ALL SELECT 35
    UNION ALL SELECT 36 UNION ALL SELECT 37 UNION ALL SELECT 38 UNION ALL SELECT 39 UNION ALL SELECT 40
    UNION ALL SELECT 41 UNION ALL SELECT 42 UNION ALL SELECT 43 UNION ALL SELECT 44 UNION ALL SELECT 45
    UNION ALL SELECT 46 UNION ALL SELECT 47 UNION ALL SELECT 48 UNION ALL SELECT 49 UNION ALL SELECT 50
    UNION ALL SELECT 51 UNION ALL SELECT 52 UNION ALL SELECT 53 UNION ALL SELECT 54 UNION ALL SELECT 55
    UNION ALL SELECT 56 UNION ALL SELECT 57 UNION ALL SELECT 58 UNION ALL SELECT 59 UNION ALL SELECT 60
    UNION ALL SELECT 61 UNION ALL SELECT 62 UNION ALL SELECT 63 UNION ALL SELECT 64 UNION ALL SELECT 65
    UNION ALL SELECT 66 UNION ALL SELECT 67 UNION ALL SELECT 68 UNION ALL SELECT 69 UNION ALL SELECT 70
    UNION ALL SELECT 71 UNION ALL SELECT 72 UNION ALL SELECT 73 UNION ALL SELECT 74 UNION ALL SELECT 75
    UNION ALL SELECT 76 UNION ALL SELECT 77 UNION ALL SELECT 78 UNION ALL SELECT 79 UNION ALL SELECT 80
    UNION ALL SELECT 81 UNION ALL SELECT 82 UNION ALL SELECT 83 UNION ALL SELECT 84 UNION ALL SELECT 85
    UNION ALL SELECT 86 UNION ALL SELECT 87 UNION ALL SELECT 88 UNION ALL SELECT 89 UNION ALL SELECT 90
    UNION ALL SELECT 91 UNION ALL SELECT 92 UNION ALL SELECT 93 UNION ALL SELECT 94 UNION ALL SELECT 95
    UNION ALL SELECT 96 UNION ALL SELECT 97 UNION ALL SELECT 98 UNION ALL SELECT 99 UNION ALL SELECT 100
) p
WHERE CAST(SUBSTRING(u.user_id, 10) AS UNSIGNED) <= 1000;

-- =====================================================
-- 검증
-- =====================================================
SELECT 'brand'     AS `table`, COUNT(*) AS count FROM brand
UNION ALL
SELECT 'product',  COUNT(*) FROM product
UNION ALL
SELECT 'inventory',COUNT(*) FROM inventory
UNION ALL
SELECT 'users',    COUNT(*) FROM users
UNION ALL
SELECT 'likes',    COUNT(*) FROM likes;

-- like_count 분포 확인
SELECT
    CASE
        WHEN like_count >= 8000 THEN 'Tier1: 8000~10000'
        WHEN like_count >= 5000 THEN 'Tier2: 5000~8000'
        WHEN like_count >= 2000 THEN 'Tier3: 2000~5000'
        WHEN like_count >= 500  THEN 'Tier4: 500~2000'
        WHEN like_count >= 100  THEN 'Tier5: 100~500'
        ELSE                        'Tail:  0~100'
    END AS tier,
    COUNT(*) AS product_count,
    MIN(like_count) AS min_like,
    MAX(like_count) AS max_like
FROM product
GROUP BY tier
ORDER BY min_like DESC;
