CREATE TABLE IF NOT EXISTS coupons (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    quantity INT
);

CREATE TABLE IF NOT EXISTS user_coupons (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    used TINYINT(1) NOT NULL,
    used_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    CONSTRAINT uk_user_coupon UNIQUE (user_id, coupon_id)
);

CREATE TABLE IF NOT EXISTS coupon_issue_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id VARCHAR(255) NOT NULL,
    coupon_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    CONSTRAINT uk_coupon_issue_request_id UNIQUE (request_id)
);

TRUNCATE TABLE coupons;
TRUNCATE TABLE user_coupons;
TRUNCATE TABLE coupon_issue_request;
TRUNCATE TABLE event_handled;