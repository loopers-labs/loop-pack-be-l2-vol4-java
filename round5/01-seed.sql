-- =====================================================================
-- Round 5 ①  상품 목록 인덱스 측정용 시딩
--   - brand 100개, product 100,000개
--   - product 에는 PK 외 인덱스를 "일부러" 만들지 않는다 (AS-IS 측정용)
--   - 각 컬럼 값은 분포를 다양하게 (특히 brand_id, like_count)
-- =====================================================================

-- 0) 깨끗한 상태에서 시작 (재실행 가능하도록)
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS brand;

-- 1) 스키마 — 엔티티(BrandModel / ProductModel / BaseEntity)와 컬럼을 일치
CREATE TABLE brand (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(500) NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    deleted_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_brand_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE product (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    brand_id       BIGINT       NOT NULL,
    name           VARCHAR(100) NOT NULL,
    description    TEXT         NULL,
    price          BIGINT       NOT NULL,
    stock_quantity INT          NOT NULL,
    image_url      VARCHAR(500) NULL,
    like_count     INT          NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    deleted_at     DATETIME(6)  NULL,
    PRIMARY KEY (id)
    -- brand_id, like_count 에 인덱스를 일부러 안 만든다 (AS-IS 측정 기준선)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2) 브랜드 100개
INSERT INTO brand (name, description, created_at, updated_at, deleted_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100
)
SELECT
    CONCAT('브랜드-', n),
    CONCAT('테스트 브랜드 ', n),
    NOW(6),
    NOW(6),
    NULL
FROM seq;

-- 3) 상품 100,000개  (재귀 CTE 가 1000줄 넘으려면 깊이 한도부터 올려야 함)
SET SESSION cte_max_recursion_depth = 200000;

INSERT INTO product
    (brand_id, name, description, price, stock_quantity, image_url, like_count,
     created_at, updated_at, deleted_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100000
)
SELECT
    -- brand_id: 1~100, 낮은 브랜드에 더 몰리게(POW로 편향) → 브랜드별 상품 수가 제각각
    FLOOR(1 + POW(RAND(), 2) * 100),
    CONCAT('상품-', n),
    CONCAT('테스트 상품 설명 ', n),
    -- price: 1,000 ~ 1,000,000 균등
    FLOOR(1000 + RAND() * 999000),
    -- stock_quantity: 0 ~ 999
    FLOOR(RAND() * 1000),
    CONCAT('https://example.com/img/', n, '.jpg'),
    -- like_count: 0 ~ 약 10만, POW(,3)으로 강하게 편향 → 대부분 적고 소수만 인기(롱테일)
    FLOOR(POW(RAND(), 3) * 100000),
    -- created_at: 최근 365일 사이로 분산
    NOW(6) - INTERVAL FLOOR(RAND() * 365) DAY,
    NOW(6),
    NULL
FROM seq;
