-- =============================================================
-- Round 5 읽기 성능 테스트용 대량 데이터 생성
-- 브랜드: 7,500개 | 유저: 10,000개 | 상품: 1,000,000개 | 좋아요: 2,000,000개 | 재고: 1,000,000개
-- 가격: 5,000 ~ 2,000,000원 (1,000원 단위)
-- =============================================================
-- 실행: /usr/local/mysql-9.7.0-macos15-arm64/bin/mysql -u application -papplication loopers_test < scripts/seed-perf-data.sql
-- 예상 소요 시간: 상품 ~3분, 좋아요 ~5분, 재고 ~3분, like_count 동기화 ~2분
-- =============================================================

SET SESSION cte_max_recursion_depth = 10000;
SET SESSION group_concat_max_len = 1000000;

-- =============================================================
-- Step 1. 브랜드 7,500개
-- =============================================================
INSERT INTO brands (name, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 7500
)
SELECT
    CONCAT('브랜드_', n),
    NOW() - INTERVAL FLOOR(RAND() * 730) DAY,
    NOW()
FROM seq;

SET @brand_first_id = LAST_INSERT_ID();

SELECT CONCAT('[1/6] 브랜드 삽입 완료. first_id=', @brand_first_id) AS log;

-- =============================================================
-- Step 2. 테스트 유저 10,000개
-- users 테이블 사용, role 컬럼 없음
-- login_id_unique_key, email_unique_key 는 VIRTUAL GENERATED 이므로 삽입 불필요
-- =============================================================
INSERT INTO users (login_id, password, name, birth_date, email, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 10000
)
SELECT
    CONCAT('perfmember', n),
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh32',
    CONCAT('성능테스트유저', n),
    DATE_SUB('1995-01-01', INTERVAL FLOOR(RAND() * 3650) DAY),
    CONCAT('perfmember', n, '@loopers-test.com'),
    NOW(),
    NOW()
FROM seq;

SET @member_first_id = LAST_INSERT_ID();

SELECT CONCAT('[2/6] 유저 삽입 완료. first_id=', @member_first_id) AS log;

-- =============================================================
-- Step 3. 상품 1,000,000개
-- 10^6 = 10×10×10×10×10×10 cross join으로 생성
-- 브랜드: 7,500개 중 랜덤, 가격: 5,000~2,000,000원 (1,000원 단위)
-- =============================================================
INSERT INTO products (brand_id, name, price, like_count, created_at, updated_at)
WITH
    d0 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    d1 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    d2 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    d3 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    d4 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    d5 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    nums AS (
        SELECT d0.n + d1.n*10 + d2.n*100 + d3.n*1000 + d4.n*10000 + d5.n*100000 + 1 AS seq
        FROM d0, d1, d2, d3, d4, d5
    )
SELECT
    @brand_first_id + FLOOR(RAND() * 7500),
    CONCAT('상품_', seq),
    FLOOR(5 + RAND() * 1996) * 1000,
    0,
    NOW() - INTERVAL FLOOR(RAND() * 365) DAY,
    NOW()
FROM nums;

SET @product_first_id = LAST_INSERT_ID();

SELECT CONCAT('[3/6] 상품 삽입 완료. first_id=', @product_first_id) AS log;

-- =============================================================
-- Step 4. 재고 1,000,000개 (상품 1개당 1개)
-- quantity: 0~500 랜덤
-- =============================================================
INSERT INTO stocks (product_id, quantity, created_at, updated_at)
WITH
    d0 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    d1 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    d2 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    d3 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    d4 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    d5 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    nums AS (
        SELECT d0.n + d1.n*10 + d2.n*100 + d3.n*1000 + d4.n*10000 + d5.n*100000 AS seq
        FROM d0, d1, d2, d3, d4, d5
    )
SELECT
    @product_first_id + seq,
    FLOOR(RAND() * 501),
    NOW(),
    NOW()
FROM nums;

SELECT CONCAT('[4/6] 재고 삽입 완료') AS log;

-- =============================================================
-- Step 5. 좋아요 2,000,000개
-- 유저 10,000명 × 200개 = 2,000,000
-- 상품은 1,000,000개 중 랜덤 선택
-- likes 테이블에 UNIQUE 제약 없으므로 일반 INSERT
-- =============================================================
INSERT INTO likes (member_id, product_id, created_at, updated_at)
WITH
    d0 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    d1 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    d2 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    d3 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    member_seq AS (
        SELECT d0.n + d1.n*10 + d2.n*100 + d3.n*1000 AS ms
        FROM d0, d1, d2, d3
    ),
    d4 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    like_seq    AS (SELECT d4.n*10 + d0.n AS ls FROM d4, d0),
    like_seq200 AS (SELECT ls FROM like_seq UNION ALL SELECT ls + 100 FROM like_seq)
SELECT
    @member_first_id + ms,
    @product_first_id + FLOOR(RAND() * 1000000),
    NOW() - INTERVAL FLOOR(RAND() * 180) DAY,
    NOW()
FROM member_seq, like_seq200;

SELECT CONCAT('[5/6] 좋아요 삽입 완료') AS log;

-- =============================================================
-- Step 6. products.like_count 동기화
-- =============================================================
UPDATE products p
INNER JOIN (
    SELECT product_id, COUNT(*) AS cnt
    FROM likes
    WHERE product_id BETWEEN @product_first_id AND @product_first_id + 999999
    GROUP BY product_id
) agg ON p.id = agg.product_id
SET p.like_count = agg.cnt
WHERE p.id BETWEEN @product_first_id AND @product_first_id + 999999;

SELECT CONCAT('[6/6] like_count 동기화 완료') AS log;

-- =============================================================
-- 검증 쿼리
-- =============================================================
SELECT
    (SELECT COUNT(*) FROM brands   WHERE id >= @brand_first_id)              AS 삽입된_브랜드,
    (SELECT COUNT(*) FROM users    WHERE id >= @member_first_id)             AS 삽입된_유저,
    (SELECT COUNT(*) FROM products WHERE id >= @product_first_id)            AS 삽입된_상품,
    (SELECT COUNT(*) FROM stocks   WHERE product_id >= @product_first_id)    AS 삽입된_재고,
    (SELECT COUNT(*) FROM likes    WHERE member_id >= @member_first_id)      AS 삽입된_좋아요;
