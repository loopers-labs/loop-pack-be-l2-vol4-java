SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE likes;
TRUNCATE TABLE product;
TRUNCATE TABLE brand;

SET FOREIGN_KEY_CHECKS = 1;

-- brand seed
INSERT INTO brand (id, name, description, created_at, updated_at, deleted_at)
VALUES
    (1,  'Nike',           'Sportswear brand', NOW(6), NOW(6), NULL),
    (2,  'Adidas',         'Sportswear brand', NOW(6), NOW(6), NULL),
    (3,  'Zara',           'Fashion brand', NOW(6), NOW(6), NULL),
    (4,  'Uniqlo',         'Casual fashion brand', NOW(6), NOW(6), NULL),
    (5,  'Apple',          'Electronics brand', NOW(6), NOW(6), NULL),
    (6,  'Samsung',        'Electronics brand', NOW(6), NOW(6), NULL),
    (7,  'Puma',           'Sportswear brand', NOW(6), NOW(6), NULL),
    (8,  'New Balance',    'Sportswear brand', NOW(6), NOW(6), NULL),
    (9,  'Under Armour',   'Sportswear brand', NOW(6), NOW(6), NULL),
    (10, 'H&M',            'Fashion brand', NOW(6), NOW(6), NULL),
    (11, 'Gucci',          'Luxury fashion brand', NOW(6), NOW(6), NULL),
    (12, 'Louis Vuitton',  'Luxury fashion brand', NOW(6), NOW(6), NULL),
    (13, 'Chanel',         'Luxury fashion brand', NOW(6), NOW(6), NULL),
    (14, 'Prada',          'Luxury fashion brand', NOW(6), NOW(6), NULL),
    (15, 'The North Face', 'Outdoor brand', NOW(6), NOW(6), NULL),
    (16, 'Patagonia',      'Outdoor brand', NOW(6), NOW(6), NULL),
    (17, 'Converse',       'Sneaker brand', NOW(6), NOW(6), NULL),
    (18, 'Reebok',         'Sportswear brand', NOW(6), NOW(6), NULL),
    (19, 'Asics',          'Sportswear brand', NOW(6), NOW(6), NULL),
    (20, 'Muji',           'Lifestyle brand', NOW(6), NOW(6), NULL);

DROP PROCEDURE IF EXISTS seed_products;

DELIMITER $$

CREATE PROCEDURE seed_products()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE v_brand_id BIGINT;
    DECLARE v_price BIGINT;
    DECLARE v_stock INT;
    DECLARE v_created_at DATETIME(6);
    DECLARE v_updated_at DATETIME(6);
    DECLARE r DOUBLE;

    WHILE i <= 100000 DO

            SET r = RAND();

            -- brand_id: 1~20 사이 랜덤
            -- 단, 일부 브랜드에 데이터가 더 많이 쏠리도록 가중치 부여
            SET v_brand_id =
                    CASE
                        WHEN r < 0.18 THEN 1
                        WHEN r < 0.32 THEN 2
                        WHEN r < 0.44 THEN 3
                        WHEN r < 0.54 THEN 4
                        WHEN r < 0.62 THEN 5
                        WHEN r < 0.69 THEN 6
                        WHEN r < 0.75 THEN 7
                        WHEN r < 0.80 THEN 8
                        WHEN r < 0.84 THEN 9
                        WHEN r < 0.87 THEN 10
                        WHEN r < 0.895 THEN 11
                        WHEN r < 0.92 THEN 12
                        WHEN r < 0.94 THEN 13
                        WHEN r < 0.955 THEN 14
                        WHEN r < 0.97 THEN 15
                        WHEN r < 0.98 THEN 16
                        WHEN r < 0.987 THEN 17
                        WHEN r < 0.993 THEN 18
                        WHEN r < 0.997 THEN 19
                        ELSE 20
                        END;

            -- price: 1,000 ~ 500,000 사이 1,000 단위
            SET v_price = FLOOR(1 + (RAND() * 500)) * 1000;

            -- stock: 0 ~ 1,000
            SET v_stock = FLOOR(RAND() * 1001);

            -- created_at: 최근 2년 이내 랜덤 날짜
            SET v_created_at = DATE_SUB(
                    NOW(6),
                    INTERVAL FLOOR(RAND() * 730 * 24 * 60 * 60) SECOND
                               );

            -- updated_at: created_at 이후 ~ 현재 사이 랜덤
            SET v_updated_at = DATE_ADD(
                    v_created_at,
                    INTERVAL FLOOR(RAND() * GREATEST(1, TIMESTAMPDIFF(SECOND, v_created_at, NOW(6)))) SECOND
                               );

            INSERT INTO product (
                brand_id,
                name,
                description,
                price,
                stock,
                created_at,
                updated_at,
                deleted_at
            )
            VALUES (
                       v_brand_id,
                       CONCAT('Product_', i),
                       CONCAT('Description for product ', i),
                       v_price,
                       v_stock,
                       v_created_at,
                       v_updated_at,
                       NULL
                   );

            SET i = i + 1;
        END WHILE;
END$$

DELIMITER ;

START TRANSACTION;

CALL seed_products();

COMMIT;

DROP PROCEDURE IF EXISTS seed_products;

-- ================================================================
-- likes seed (약 200,000~250,000건, user_id 1~2000 가상 유저 사용)
-- INSERT IGNORE로 unique 충돌 자동 스킵
-- ================================================================

DROP PROCEDURE IF EXISTS seed_likes;

DELIMITER $$

CREATE PROCEDURE seed_likes()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE v_user_id BIGINT;
    DECLARE v_product_id BIGINT;
    DECLARE v_created_at DATETIME(6);

    WHILE i <= 400000 DO

        -- user_id: 1~2000 랜덤 (가상 유저)
        SET v_user_id = FLOOR(1 + RAND() * 2000);

        -- product_id: 1~100000 랜덤 (일부 상품에 쏠리도록 POW 적용)
        SET v_product_id = FLOOR(1 + POW(RAND(), 2) * 99999);

        SET v_created_at = DATE_SUB(
            NOW(6),
            INTERVAL FLOOR(RAND() * 730 * 24 * 60 * 60) SECOND
        );

        INSERT IGNORE INTO likes (user_id, product_id, created_at, updated_at, deleted_at)
        VALUES (v_user_id, v_product_id, v_created_at, v_created_at, NULL);

        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

START TRANSACTION;

CALL seed_likes();

COMMIT;

DROP PROCEDURE IF EXISTS seed_likes;

-- ================================================================
-- like_count 동기화 (likes 테이블 기준으로 product.like_count 초기화)
-- ================================================================
UPDATE product p
JOIN (
    SELECT product_id, COUNT(*) AS cnt FROM likes GROUP BY product_id
) l ON p.id = l.product_id
SET p.like_count = l.cnt;