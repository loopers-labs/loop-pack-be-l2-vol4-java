-- Physical snapshot and restart staging tables for productRankingAggregationJob.
-- product_metrics must already exist before this script is applied.

CREATE TABLE mv_product_rank_weekly (
    aggregation_date DATE NOT NULL,
    period_start_date DATE NOT NULL,
    period_end_date DATE NOT NULL,
    rank_position SMALLINT UNSIGNED NOT NULL,
    product_id BIGINT NOT NULL,
    score DECIMAL(30, 1) NOT NULL,
    generated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (aggregation_date, rank_position),
    CONSTRAINT uk_mv_product_rank_weekly_date_product
        UNIQUE (aggregation_date, product_id),
    CONSTRAINT chk_mv_product_rank_weekly_rank
        CHECK (rank_position BETWEEN 1 AND 100),
    CONSTRAINT chk_mv_product_rank_weekly_period
        CHECK (period_start_date <= period_end_date AND period_end_date = aggregation_date)
) ENGINE=InnoDB;

CREATE TABLE mv_product_rank_monthly (
    aggregation_date DATE NOT NULL,
    period_start_date DATE NOT NULL,
    period_end_date DATE NOT NULL,
    rank_position SMALLINT UNSIGNED NOT NULL,
    product_id BIGINT NOT NULL,
    score DECIMAL(30, 1) NOT NULL,
    generated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (aggregation_date, rank_position),
    CONSTRAINT uk_mv_product_rank_monthly_date_product
        UNIQUE (aggregation_date, product_id),
    CONSTRAINT chk_mv_product_rank_monthly_rank
        CHECK (rank_position BETWEEN 1 AND 100),
    CONSTRAINT chk_mv_product_rank_monthly_period
        CHECK (period_start_date <= period_end_date AND period_end_date = aggregation_date)
) ENGINE=InnoDB;

CREATE TABLE stg_product_rank_aggregation (
    job_instance_id BIGINT NOT NULL,
    period_type VARCHAR(10) NOT NULL,
    product_id BIGINT NOT NULL,
    score DECIMAL(30, 1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (job_instance_id, period_type, product_id),
    INDEX idx_stg_product_rank_top (
        job_instance_id,
        period_type,
        score DESC,
        product_id ASC
    ),
    CONSTRAINT chk_stg_product_rank_period_type
        CHECK (period_type IN ('WEEKLY', 'MONTHLY'))
) ENGINE=InnoDB;
