CREATE TABLE product_daily_metrics (
    metric_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    order_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (metric_date, product_id),
    INDEX idx_daily_metrics_product (product_id, metric_date)
);
