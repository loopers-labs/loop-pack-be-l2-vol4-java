-- ============================================================
-- 상품 INSERT 벤치마크 프로시저
--
-- 실행: CALL bench_insert(1000);
--   → 1000건 INSERT 후 total_ms / per_insert_ms 출력
--   → 완료 후 테스트 데이터 자동 DELETE
--
-- 실행 순서:
--   1단계 — 현재 인덱스 상태에서 CALL bench_insert(1000)
--   2단계 — ALTER TABLE DROP INDEX 후 CALL bench_insert(1000)
--   3단계 — 인덱스 복원 후 CALL bench_insert(1000)
-- ============================================================

DROP PROCEDURE IF EXISTS bench_insert;

DELIMITER $$

CREATE PROCEDURE bench_insert(IN runs INT)
BEGIN
    DECLARE i       INT DEFAULT 0;
    DECLARE t0      DATETIME(6);
    DECLARE elapsed BIGINT;
    DECLARE bid     BINARY(16);

    SELECT id INTO bid FROM brands WHERE deleted_at IS NULL LIMIT 1;

    SET t0 = NOW(6);
    WHILE i < runs DO
        SET i = i + 1;
        INSERT INTO products (id, brand_id, name, description, price, like_count, likes_purged, created_at, updated_at)
        VALUES (
            UUID_TO_BIN(UUID()),
            bid,
            CONCAT('BENCH_', LPAD(i, 6, '0')),
            'bench insert test',
            10000,
            0,
            0,
            NOW(),
            NOW()
        );
    END WHILE;
    SET elapsed = TIMESTAMPDIFF(MICROSECOND, t0, NOW(6));

    SELECT
        runs                              AS 삽입건수,
        ROUND(elapsed / 1000, 2)          AS total_ms,
        ROUND(elapsed / 1000 / runs, 3)   AS per_insert_ms;

    DELETE FROM products WHERE name LIKE 'BENCH_%';
END$$

DELIMITER ;

-- ============================================================
-- 1단계: 현재 인덱스 상태로 실행
-- ============================================================
CALL bench_insert(1000);

-- ============================================================
-- 2단계: 인덱스 제거 후 실행
--   아래 ALTER TABLE 실행 후 CALL bench_insert(1000) 재실행
-- ============================================================
-- ALTER TABLE products
--   DROP INDEX idx_products_deleted_at,
--   DROP INDEX idx_products_brand_deleted;
-- CALL bench_insert(1000);

-- ============================================================
-- 6개 인덱스 제거용 (6개 상태에서 0개로 갈 때)
-- ============================================================
-- ALTER TABLE products
--   DROP INDEX idx_products_deleted_latest,
--   DROP INDEX idx_products_deleted_likes,
--   DROP INDEX idx_products_deleted_price,
--   DROP INDEX idx_products_brand_deleted_latest,
--   DROP INDEX idx_products_brand_deleted_likes,
--   DROP INDEX idx_products_brand_deleted_price;
-- CALL bench_insert(1000);
