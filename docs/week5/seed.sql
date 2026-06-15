-- 성능 테스트용 시딩 스크립트
-- 브랜드 10개 + 상품 100,000개 + 재고 100,000개
-- 실행: docker exec -i mysql mysql -uroot -proot loopers < docs/week5/seed.sql

USE loopers;

-- 브랜드 10개
INSERT INTO brand (name, created_at, updated_at) VALUES
  ('나이키',    NOW(), NOW()),
  ('아디다스',  NOW(), NOW()),
  ('뉴발란스',  NOW(), NOW()),
  ('푸마',      NOW(), NOW()),
  ('언더아머',  NOW(), NOW()),
  ('리복',      NOW(), NOW()),
  ('컨버스',    NOW(), NOW()),
  ('반스',      NOW(), NOW()),
  ('살로몬',    NOW(), NOW()),
  ('호카',      NOW(), NOW());

DROP PROCEDURE IF EXISTS seed_products;

DELIMITER //
CREATE PROCEDURE seed_products()
BEGIN
    DECLARE i       INT     DEFAULT 1;
    DECLARE base_id BIGINT;

    -- 브랜드 최소 id 를 기준으로 0~9 offset 계산
    SELECT MIN(id) INTO base_id FROM brand;

    SET autocommit = 0;

    WHILE i <= 100000 DO
        INSERT INTO product (brand_id, name, price, like_count, created_at, updated_at)
        VALUES (
            base_id + ((i - 1) % 10),
            CONCAT('상품-', LPAD(i, 6, '0')),
            FLOOR(RAND() * 990000) + 10000,
            FLOOR(RAND() * 10000),
            NOW() - INTERVAL FLOOR(RAND() * 365) DAY,
            NOW()
        );

        INSERT INTO stock (product_id, quantity, created_at, updated_at)
        VALUES (LAST_INSERT_ID(), FLOOR(RAND() * 1000), NOW(), NOW());

        -- 1000건마다 커밋 (트랜잭션 로그 크기 제한)
        IF i % 1000 = 0 THEN
            COMMIT;
        END IF;

        SET i = i + 1;
    END WHILE;

    COMMIT;
    SET autocommit = 1;
END //
DELIMITER ;

CALL seed_products();
DROP PROCEDURE IF EXISTS seed_products;

SELECT COUNT(*) AS product_count FROM product;
SELECT COUNT(*) AS stock_count   FROM stock;
