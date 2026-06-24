-- ============================================================
-- 상품 조회 쿼리 벤치마크 (BEFORE 상태 재현)
-- IGNORE INDEX 힌트로 커스텀 인덱스 무력화
--
-- 실행: CALL bench_products_before(10);
--
-- IGNORE 전략:
--   ①②⑤ 전체 쿼리  : 커스텀 인덱스 6개 전부 무시 → ALL 스캔
--   ③④⑥ 브랜드 쿼리 : 브랜드 복합 인덱스만 무시, FK 유지 → ref 스캔
-- ============================================================

DROP PROCEDURE IF EXISTS bench_products_before;

DELIMITER $$

CREATE PROCEDURE bench_products_before(IN runs INT)
BEGIN
    DECLARE i        INT;
    DECLARE t0       DATETIME(6);
    DECLARE elapsed  BIGINT;
    DECLARE dummy    INT;
    DECLARE bid      BINARY(16);

    SELECT id INTO bid FROM brands WHERE deleted_at IS NULL ORDER BY created_at LIMIT 1;

    DROP TEMPORARY TABLE IF EXISTS bench_results_before;
    CREATE TEMPORARY TABLE bench_results_before (
        query_name VARCHAR(60),
        run_no     INT,
        ms         DECIMAL(10, 3)
    );

    -- ① 전체 활성 + 최신순
    SET i = 0;
    WHILE i < runs DO
        SET i = i + 1;
        SET t0 = NOW(6);
        SELECT COUNT(*) INTO dummy FROM (
            SELECT id FROM products
            IGNORE INDEX (idx_products_deleted_latest, idx_products_deleted_likes, idx_products_deleted_price,
                          idx_products_brand_deleted_latest, idx_products_brand_deleted_likes, idx_products_brand_deleted_price)
            WHERE deleted_at IS NULL
            ORDER BY created_at DESC
            LIMIT 20
        ) q;
        SET elapsed = TIMESTAMPDIFF(MICROSECOND, t0, NOW(6));
        IF i > 1 THEN
            INSERT INTO bench_results_before VALUES ('① 전체+최신순', i, elapsed / 1000);
        END IF;
    END WHILE;

    -- ② 전체 활성 + 좋아요순
    SET i = 0;
    WHILE i < runs DO
        SET i = i + 1;
        SET t0 = NOW(6);
        SELECT COUNT(*) INTO dummy FROM (
            SELECT id FROM products
            IGNORE INDEX (idx_products_deleted_latest, idx_products_deleted_likes, idx_products_deleted_price,
                          idx_products_brand_deleted_latest, idx_products_brand_deleted_likes, idx_products_brand_deleted_price)
            WHERE deleted_at IS NULL
            ORDER BY like_count DESC
            LIMIT 20
        ) q;
        SET elapsed = TIMESTAMPDIFF(MICROSECOND, t0, NOW(6));
        IF i > 1 THEN
            INSERT INTO bench_results_before VALUES ('② 전체+좋아요순', i, elapsed / 1000);
        END IF;
    END WHILE;

    -- ⑤ 전체 활성 + 가격순
    SET i = 0;
    WHILE i < runs DO
        SET i = i + 1;
        SET t0 = NOW(6);
        SELECT COUNT(*) INTO dummy FROM (
            SELECT id FROM products
            IGNORE INDEX (idx_products_deleted_latest, idx_products_deleted_likes, idx_products_deleted_price,
                          idx_products_brand_deleted_latest, idx_products_brand_deleted_likes, idx_products_brand_deleted_price)
            WHERE deleted_at IS NULL
            ORDER BY price ASC
            LIMIT 20
        ) q;
        SET elapsed = TIMESTAMPDIFF(MICROSECOND, t0, NOW(6));
        IF i > 1 THEN
            INSERT INTO bench_results_before VALUES ('⑤ 전체+가격순', i, elapsed / 1000);
        END IF;
    END WHILE;

    -- ③ 브랜드 필터 + 최신순 (FK 인덱스 유지)
    SET i = 0;
    WHILE i < runs DO
        SET i = i + 1;
        SET t0 = NOW(6);
        SELECT COUNT(*) INTO dummy FROM (
            SELECT id FROM products
            IGNORE INDEX (idx_products_brand_deleted_latest, idx_products_brand_deleted_likes, idx_products_brand_deleted_price)
            WHERE brand_id = bid
              AND deleted_at IS NULL
            ORDER BY created_at DESC
            LIMIT 20
        ) q;
        SET elapsed = TIMESTAMPDIFF(MICROSECOND, t0, NOW(6));
        IF i > 1 THEN
            INSERT INTO bench_results_before VALUES ('③ 브랜드+최신순', i, elapsed / 1000);
        END IF;
    END WHILE;

    -- ④ 브랜드 필터 + 좋아요순 (FK 인덱스 유지)
    SET i = 0;
    WHILE i < runs DO
        SET i = i + 1;
        SET t0 = NOW(6);
        SELECT COUNT(*) INTO dummy FROM (
            SELECT id FROM products
            IGNORE INDEX (idx_products_brand_deleted_latest, idx_products_brand_deleted_likes, idx_products_brand_deleted_price)
            WHERE brand_id = bid
              AND deleted_at IS NULL
            ORDER BY like_count DESC
            LIMIT 20
        ) q;
        SET elapsed = TIMESTAMPDIFF(MICROSECOND, t0, NOW(6));
        IF i > 1 THEN
            INSERT INTO bench_results_before VALUES ('④ 브랜드+좋아요순', i, elapsed / 1000);
        END IF;
    END WHILE;

    -- ⑥ 브랜드 필터 + 가격순 (FK 인덱스 유지)
    SET i = 0;
    WHILE i < runs DO
        SET i = i + 1;
        SET t0 = NOW(6);
        SELECT COUNT(*) INTO dummy FROM (
            SELECT id FROM products
            IGNORE INDEX (idx_products_brand_deleted_latest, idx_products_brand_deleted_likes, idx_products_brand_deleted_price)
            WHERE brand_id = bid
              AND deleted_at IS NULL
            ORDER BY price ASC
            LIMIT 20
        ) q;
        SET elapsed = TIMESTAMPDIFF(MICROSECOND, t0, NOW(6));
        IF i > 1 THEN
            INSERT INTO bench_results_before VALUES ('⑥ 브랜드+가격순', i, elapsed / 1000);
        END IF;
    END WHILE;

    SELECT
        query_name                AS 쿼리,
        ROUND(AVG(ms), 2)        AS avg_ms,
        ROUND(MIN(ms), 2)        AS min_ms,
        ROUND(MAX(ms), 2)        AS max_ms,
        COUNT(*)                 AS 측정횟수
    FROM bench_results_before
    GROUP BY query_name
    ORDER BY query_name;

    DROP TEMPORARY TABLE IF EXISTS bench_results_before;
END$$

DELIMITER ;

-- ============================================================
-- 실행
-- ============================================================
CALL bench_products_before(10);
