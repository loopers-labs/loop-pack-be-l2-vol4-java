-- =====================================================================
-- Round 5 — 벤치마크 전용 스키마 (products, likes)
-- =====================================================================
-- 실제 앱(JPA ddl-auto)이 만드는 스키마와 인덱스 관점에서 동등하다.
-- 앱 기동(redis/kafka 의존 + ddl-auto=create 의 drop 위험)을 피하기 위해
-- 측정에 필요한 두 테이블만 독립적으로 생성한다.
-- AS-IS: products 는 PK 뿐, likes 는 (user_id, product_id) UK 뿐 — 조회용 인덱스 없음.
-- =====================================================================

CREATE TABLE IF NOT EXISTS products (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    brand_id    BIGINT       NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    price       BIGINT       NOT NULL,
    status      VARCHAR(255) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    deleted_at  DATETIME(6)  NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS likes (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_likes_user_product (user_id, product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
