-- =====================================================================
-- Week7 (이벤트·Kafka·Outbox) 로컬 스키마 + 시드
--
-- 로컬 프로파일은 ddl-auto=none 이라 앱이 테이블을 만들지 않는다.
-- 신규 엔티티 4개의 테이블과 coupon.quantity 컬럼을 이 스크립트로 한 번 만들어 둔다.
-- (Kafka 토픽 order-events/catalog-events/coupon-issue-requests/*.DLT 는
--  앱 기동 시 KafkaAdmin 이 NewTopic 빈으로 자동 생성하므로 여기서 다루지 않는다.)
--
-- 실행 예:
--   docker exec -i <mysql_container> mysql -uroot -proot loopers < docs/local/week7-schema-seed.sql
-- =====================================================================

-- 1) Outbox (commerce-api) ------------------------------------------------
CREATE TABLE IF NOT EXISTS outbox_event (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    deleted_at   DATETIME(6)  NULL,
    topic        VARCHAR(255) NOT NULL,
    message_key  VARCHAR(255) NULL,
    event_type   VARCHAR(255) NOT NULL,
    payload      TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    published_at DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_outbox_status_id (status, id)   -- 릴레이의 PENDING 조회용
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- (멱등 가드 테이블 event_handled 는 제거됨: 멱등을 설계로 확보한다.
--  쿠폰=요청 status + user_coupon 유니크, 판매/좋아요=SSOT 재계산, 조회=근사. docs/week7 설계 참고)

-- 3) 상품 집계 (commerce-streamer) — SSOT(likes/order_items) 위의 MV ------
CREATE TABLE IF NOT EXISTS product_metrics (
    product_id  BIGINT      NOT NULL,
    sales_count BIGINT      NOT NULL DEFAULT 0,
    like_count  BIGINT      NOT NULL DEFAULT 0,
    view_count  BIGINT      NOT NULL DEFAULT 0,
    updated_at  DATETIME(6) NULL,
    PRIMARY KEY (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 3-1) DLQ 운영 테이블 (commerce-streamer) — .DLT 적재 후 조회/재처리/폐기 --
CREATE TABLE IF NOT EXISTS dlq_message (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    deleted_at         DATETIME(6)  NULL,
    original_topic     VARCHAR(255) NOT NULL,
    original_partition INT          NULL,
    original_offset    BIGINT       NULL,
    message_key        VARCHAR(255) NULL,
    payload            TEXT         NULL,
    exception_class    VARCHAR(500) NULL,
    exception_message  TEXT         NULL,
    status             VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_dlq_status (status),
    KEY idx_dlq_topic (original_topic)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 4) 선착순 발급 요청 (commerce-api 작성 / commerce-streamer 갱신) --------
CREATE TABLE IF NOT EXISTS coupon_issue_request (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    deleted_at DATETIME(6)  NULL,
    request_id VARCHAR(255) NOT NULL,
    coupon_id  BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    reason     VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_issue_request_id (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 5) coupon 에 선착순 관련 컬럼 추가 -------------------------------------
--    이미 있으면 "Duplicate column" 에러가 나므로 최초 1회만 실행.
--    issued_count: Consumer 가 원자적 조건부 UPDATE(issued_count < quantity)로 초과 발급을 막는다.
ALTER TABLE coupon ADD COLUMN quantity BIGINT NULL;
ALTER TABLE coupon ADD COLUMN issued_count BIGINT NOT NULL DEFAULT 0;

-- 6) 시드: 선착순 100명 한정 쿠폰 ---------------------------------------
--    products/brands/users 는 admin 시드 엔드포인트로 넣는 게 편하다(스키마가 길어 생략).
INSERT INTO coupon (created_at, updated_at, name, type, value, min_order_amount, expired_at, quantity)
VALUES (NOW(6), NOW(6), '선착순 100명 3천원', 'FIXED', 3000, 10000, '2030-12-31 23:59:59', 100);
