-- =============================================================
-- 인기 상품 좋아요 집중 분포 생성
-- 상품 id 1~1000을 인기 상품으로 지정, 각 ~500개 좋아요 추가
-- 총 추가: 500,000개 / 실행 후 like_count 재동기화
-- =============================================================
-- 실행: /usr/local/mysql-9.7.0-macos15-arm64/bin/mysql -u application -papplication loopers_test < scripts/seed-popular-likes.sql
-- =============================================================

SET SESSION cte_max_recursion_depth = 10000;

-- =============================================================
-- Step 1. 인기 상품 1,000개에 500개씩 좋아요 추가 (500,000건)
-- popular: product_id 1~1000
-- users: id 1~10000 중 랜덤
-- =============================================================
INSERT INTO likes (member_id, product_id, created_at, updated_at)
WITH
    d0 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    d1 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    d2 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    -- popular: 1~1000
    popular AS (
        SELECT d0.n + d1.n * 10 + d2.n * 100 + 1 AS pid
        FROM d0, d1, d2
    ),
    d3 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    d4 AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
           UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    -- 500개 시퀀스 (0~99 × 5배)
    base100 AS (SELECT d3.n * 10 + d4.n AS ls FROM d3, d4),
    like_seq500 AS (
        SELECT ls FROM base100
        UNION ALL SELECT ls + 100 FROM base100
        UNION ALL SELECT ls + 200 FROM base100
        UNION ALL SELECT ls + 300 FROM base100
        UNION ALL SELECT ls + 400 FROM base100
    )
SELECT
    1 + FLOOR(RAND() * 10000),
    pid,
    NOW() - INTERVAL FLOOR(RAND() * 180) DAY,
    NOW()
FROM popular, like_seq500;

SELECT CONCAT('[1/2] 인기 상품 좋아요 삽입 완료. 추가된 행: ', ROW_COUNT()) AS log;

-- =============================================================
-- Step 2. 인기 상품 like_count 재동기화
-- =============================================================
UPDATE products p
INNER JOIN (
    SELECT product_id, COUNT(*) AS cnt
    FROM likes
    WHERE product_id BETWEEN 1 AND 1000
    GROUP BY product_id
) agg ON p.id = agg.product_id
SET p.like_count = agg.cnt
WHERE p.id BETWEEN 1 AND 1000;

SELECT CONCAT('[2/2] like_count 재동기화 완료') AS log;

-- =============================================================
-- 검증
-- =============================================================
SELECT
    MIN(like_count) AS 최소_좋아요,
    MAX(like_count) AS 최대_좋아요,
    ROUND(AVG(like_count), 2) AS 평균_좋아요,
    SUM(CASE WHEN like_count >= 100 THEN 1 ELSE 0 END) AS 좋아요_100이상_상품수,
    SUM(CASE WHEN like_count >= 300 THEN 1 ELSE 0 END) AS 좋아요_300이상_상품수
FROM products
WHERE id BETWEEN 1 AND 1000;
