-- ============================================================
-- 상품 조회 쿼리 벤치마크 프로시저
--
-- 실행: CALL bench_products(10);
--   → 6개 쿼리 각 10회 실행 (1번째 콜드캐시 제외, 2~10회 평균)
--
-- BEFORE(인덱스 없음) / AFTER(인덱스 추가) 각각 실행해서 비교
-- ============================================================

DROP PROCEDURE IF EXISTS bench_products;

DELIMITER $$

CREATE PROCEDURE bench_products(IN runs INT)
BEGIN
    DECLARE i        INT;
    DECLARE t0       DATETIME(6);
    DECLARE elapsed  BIGINT;
    DECLARE dummy    INT;
    DECLARE bid      BINARY(16);

    SELECT id INTO bid FROM brands WHERE deleted_at IS NULL ORDER BY created_at LIMIT 1;

    DROP TEMPORARY TABLE IF EXISTS bench_results;
    CREATE TEMPORARY TABLE bench_results (
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
            SELECT * FROM products
            WHERE deleted_at IS NULL
            ORDER BY created_at DESC
            LIMIT 20
        ) q;
        SET elapsed = TIMESTAMPDIFF(MICROSECOND, t0, NOW(6));
        IF i > 1 THEN INSERT INTO bench_results VALUES ('① 전체+최신순', i, elapsed / 1000); END IF;
    END WHILE;

    -- ② 전체 활성 + 좋아요순
    SET i = 0;
    WHILE i < runs DO
        SET i = i + 1;
        SET t0 = NOW(6);
        SELECT COUNT(*) INTO dummy FROM (
            SELECT * FROM products
            WHERE deleted_at IS NULL
            ORDER BY like_count DESC
            LIMIT 20
        ) q;
        SET elapsed = TIMESTAMPDIFF(MICROSECOND, t0, NOW(6));
        IF i > 1 THEN INSERT INTO bench_results VALUES ('② 전체+좋아요순', i, elapsed / 1000); END IF;
    END WHILE;

    -- ⑤ 전체 활성 + 가격순
    SET i = 0;
    WHILE i < runs DO
        SET i = i + 1;
        SET t0 = NOW(6);
        SELECT COUNT(*) INTO dummy FROM (
            SELECT * FROM products
            WHERE deleted_at IS NULL
            ORDER BY price ASC
            LIMIT 20
        ) q;
        SET elapsed = TIMESTAMPDIFF(MICROSECOND, t0, NOW(6));
        IF i > 1 THEN INSERT INTO bench_results VALUES ('⑤ 전체+가격순', i, elapsed / 1000); END IF;
    END WHILE;

    -- ③ 브랜드 필터 + 최신순
    SET i = 0;
    WHILE i < runs DO
        SET i = i + 1;
        SET t0 = NOW(6);
        SELECT COUNT(*) INTO dummy FROM (
            SELECT * FROM products
            WHERE brand_id = bid
              AND deleted_at IS NULL
            ORDER BY created_at DESC
            LIMIT 20
        ) q;
        SET elapsed = TIMESTAMPDIFF(MICROSECOND, t0, NOW(6));
        IF i > 1 THEN INSERT INTO bench_results VALUES ('③ 브랜드+최신순', i, elapsed / 1000); END IF;
    END WHILE;

    -- ④ 브랜드 필터 + 좋아요순
    SET i = 0;
    WHILE i < runs DO
        SET i = i + 1;
        SET t0 = NOW(6);
        SELECT COUNT(*) INTO dummy FROM (
            SELECT * FROM products
            WHERE brand_id = bid
              AND deleted_at IS NULL
            ORDER BY like_count DESC
            LIMIT 20
        ) q;
        SET elapsed = TIMESTAMPDIFF(MICROSECOND, t0, NOW(6));
        IF i > 1 THEN INSERT INTO bench_results VALUES ('④ 브랜드+좋아요순', i, elapsed / 1000); END IF;
    END WHILE;

    -- ⑥ 브랜드 필터 + 가격순
    SET i = 0;
    WHILE i < runs DO
        SET i = i + 1;
        SET t0 = NOW(6);
        SELECT COUNT(*) INTO dummy FROM (
            SELECT * FROM products
            WHERE brand_id = bid
              AND deleted_at IS NULL
            ORDER BY price ASC
            LIMIT 20
        ) q;
        SET elapsed = TIMESTAMPDIFF(MICROSECOND, t0, NOW(6));
        IF i > 1 THEN INSERT INTO bench_results VALUES ('⑥ 브랜드+가격순', i, elapsed / 1000); END IF;
    END WHILE;

    SELECT
        query_name                AS 쿼리,
        ROUND(AVG(ms), 2)        AS avg_ms,
        ROUND(MIN(ms), 2)        AS min_ms,
        ROUND(MAX(ms), 2)        AS max_ms,
        COUNT(*)                 AS 측정횟수
    FROM bench_results
    GROUP BY query_name
    ORDER BY query_name;

    DROP TEMPORARY TABLE IF EXISTS bench_results;
END$$

DELIMITER ;

-- ============================================================
-- 실행
-- ============================================================
CALL bench_products(10);
