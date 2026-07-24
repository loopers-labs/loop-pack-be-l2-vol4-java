-- product_metrics 는 commerce-streamer 가 소유하는 테이블이다(이 배치는 읽기만 한다).
-- 배치 모듈에는 해당 엔티티가 없어 ddl-auto 가 만들어주지 않으므로, 테스트에서만 계약과 동일한 형태로 만든다.
-- 원본: apps/commerce-streamer/.../domain/metrics/ProductMetrics.java
CREATE TABLE IF NOT EXISTS product_metrics
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    product_id   BIGINT      NOT NULL,
    metric_date  DATE        NOT NULL,
    like_count   BIGINT      NOT NULL,
    like_delta   BIGINT      NOT NULL,
    sales_count  BIGINT      NOT NULL,
    view_count   BIGINT      NOT NULL,
    like_version BIGINT      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    deleted_at   DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_metrics_product_date (product_id, metric_date),
    KEY idx_product_metrics_date (metric_date)
);
