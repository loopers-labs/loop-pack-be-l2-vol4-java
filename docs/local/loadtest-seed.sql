-- =====================================================================
-- Week8 부하테스트 시드 (로컬)
--   - 대용량 재고 상품 1개 (주문용)
--   - 부하용 쿠폰 1개 (min_order 0, 대량 수량) — 유저별로 자동 발급
--   - load-1..load-N 유저 (비밀번호 = loopers01 과 동일: Passw0rd!)
--   - 각 유저에게 user_coupon 1장(AVAILABLE) 발급 → k6 가 GET /users/me/coupons 로 사용
--
-- 실행: docker exec -i docker-mysql-1 mysql -uroot -proot loopers < docs/local/loadtest-seed.sql
-- 유저 수 조절: 아래 CALL seed_load_users(500) 의 인자.
-- =====================================================================

-- 1) 대용량 재고 상품 (brand_id=1 사용) --------------------------------
INSERT INTO products (brand_id, created_at, updated_at, price, name, description, thumbnail_url, status, like_count)
VALUES (1, NOW(6), NOW(6), 10000, 'loadtest-product', '부하테스트용', 'x.jpg', 'ON_SALE', 0);
SET @pid = LAST_INSERT_ID();

INSERT INTO product_stocks (quantity, created_at, updated_at, product_id)
VALUES (10000000, NOW(6), NOW(6), @pid);

-- 2) 부하용 쿠폰 (min_order 0 이라 항상 적용) --------------------------
INSERT INTO coupon (created_at, updated_at, name, type, value, min_order_amount, expired_at, quantity, issued_count)
VALUES (NOW(6), NOW(6), 'loadtest-3000', 'FIXED', 3000, 0, '2030-12-31 23:59:59', 1000000, 0);
SET @cid = LAST_INSERT_ID();

-- 3) 유저 + 유저별 쿠폰 발급 -------------------------------------------
DROP PROCEDURE IF EXISTS seed_load_users;
DELIMITER $$
CREATE PROCEDURE seed_load_users(IN cnt INT)
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE pw VARCHAR(255);
    SELECT password INTO pw FROM users WHERE login_id = 'loopers01';
    WHILE i <= cnt DO
        INSERT INTO users (created_at, updated_at, login_id, password, name, email, birth_date)
        VALUES (NOW(6), NOW(6), CONCAT('load-', i), pw, CONCAT('부하', i), CONCAT('load', i, '@ex.com'), '1995-01-01');
        INSERT INTO user_coupon (coupon_id, created_at, updated_at, expired_at, min_order_amount, user_id, value, version, status, type)
        VALUES (@cid, NOW(6), NOW(6), '2030-12-31 23:59:59', 0, LAST_INSERT_ID(), 3000, 0, 'AVAILABLE', 'FIXED');
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL seed_load_users(1000);
DROP PROCEDURE seed_load_users;

-- 4) 확인용 출력 (k6 는 이 product_id 로 주문) --------------------------
SELECT @pid AS loadtest_product_id, @cid AS loadtest_coupon_id,
       (SELECT COUNT(*) FROM users WHERE login_id LIKE 'load-%') AS load_users;
