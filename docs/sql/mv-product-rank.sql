CREATE TABLE mv_product_rank_weekly (
    period_key VARCHAR(10) NOT NULL,
    product_id BIGINT NOT NULL,
    rank_position INT NOT NULL,
    score DOUBLE NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (period_key, product_id),
    UNIQUE KEY uk_mv_weekly_rank (period_key, rank_position)
);

CREATE TABLE mv_product_rank_monthly (
    period_key VARCHAR(6) NOT NULL,
    product_id BIGINT NOT NULL,
    rank_position INT NOT NULL,
    score DOUBLE NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (period_key, product_id),
    UNIQUE KEY uk_mv_monthly_rank (period_key, rank_position)
);

CREATE TABLE mv_product_rank_staging (
    period_type VARCHAR(10) NOT NULL,
    period_key VARCHAR(10) NOT NULL,
    product_id BIGINT NOT NULL,
    rank_position INT NOT NULL,
    score DOUBLE NOT NULL,
    PRIMARY KEY (period_type, period_key, product_id),
    UNIQUE KEY uk_staging_rank (period_type, period_key, rank_position)
);
