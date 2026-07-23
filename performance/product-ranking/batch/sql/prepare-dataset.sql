CREATE TABLE IF NOT EXISTS product_metrics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    metric_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    view_count BIGINT NOT NULL,
    like_count BIGINT NOT NULL,
    order_count BIGINT NOT NULL,
    order_quantity BIGINT NOT NULL,
    order_amount BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_metrics_date_product UNIQUE (metric_date, product_id),
    INDEX idx_product_metrics_date (metric_date)
) ENGINE=InnoDB;

-- The harness owns only this high product-id range. Application data outside the range is untouched.
DELETE FROM product_metrics
WHERE product_id > @product_id_base
  AND product_id <= @product_id_base + @max_daily_products;
