-- 주간/월간 랭킹 MV 는 commerce-batch 가 소유하는 테이블이다(이 앱은 읽기만 한다).
-- 조회 앱에 엔티티를 두면 ddl-auto 가 배치 적재분을 날리므로 매핑하지 않는다 → 테스트에서만 계약과 동일한 형태로 만든다.
-- 원본: apps/commerce-batch/.../domain/ranking/MvProductRankWeekly.java, MvProductRankMonthly.java
CREATE TABLE IF NOT EXISTS mv_product_rank_weekly
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    period_key  VARCHAR(16)  NOT NULL,
    product_id  BIGINT       NOT NULL,
    rank_no     INT          NULL,
    score       DOUBLE       NOT NULL,
    like_count  BIGINT       NOT NULL,
    order_count BIGINT       NOT NULL,
    view_count  BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    deleted_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mv_rank_weekly_key_product (period_key, product_id),
    KEY idx_mv_rank_weekly_key_rank (period_key, rank_no)
);

CREATE TABLE IF NOT EXISTS mv_product_rank_monthly
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    period_key  VARCHAR(16)  NOT NULL,
    product_id  BIGINT       NOT NULL,
    rank_no     INT          NULL,
    score       DOUBLE       NOT NULL,
    like_count  BIGINT       NOT NULL,
    order_count BIGINT       NOT NULL,
    view_count  BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    deleted_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mv_rank_monthly_key_product (period_key, product_id),
    KEY idx_mv_rank_monthly_key_rank (period_key, rank_no)
);
