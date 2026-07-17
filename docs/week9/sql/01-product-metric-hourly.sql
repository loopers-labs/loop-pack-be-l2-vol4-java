-- Apply manually, or through the deployment platform's migration runner, before deploying streamer/batch code.
-- This repository does not include an automatic migration runner.
CREATE TABLE IF NOT EXISTS product_metric_hourly (
    id BIGINT NOT NULL AUTO_INCREMENT,
    metric_date DATE NOT NULL,
    metric_hour INT NOT NULL,
    product_id BIGINT NOT NULL,
    like_count BIGINT NOT NULL,
    view_count BIGINT NOT NULL,
    sales_count BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_metric_hourly_date_hour_product
        UNIQUE (metric_date, metric_hour, product_id),
    INDEX idx_product_metric_hourly_date_product (metric_date, product_id)
);

-- Rollback is intentionally not automatic because this table is the reconciliation source of truth.
-- After backing up or confirming the data is disposable, the explicit rollback is:
-- DROP TABLE product_metric_hourly;
