CREATE TABLE product_rank_staging (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_key VARCHAR(80) NOT NULL,
    period_type VARCHAR(10) NOT NULL,
    period_start DATE NOT NULL,
    job_instance_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    score DECIMAL(19, 4) NOT NULL,
    view_count BIGINT NOT NULL,
    like_count BIGINT NOT NULL,
    sales_count BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_rank_staging_run_product UNIQUE (run_key, product_id),
    INDEX idx_product_rank_staging_publish (run_key, score DESC, product_id ASC),
    INDEX idx_product_rank_staging_cleanup (created_at, period_type, period_start)
) ENGINE=InnoDB;

CREATE TABLE mv_product_rank_weekly (
    id BIGINT NOT NULL AUTO_INCREMENT,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    product_id BIGINT NOT NULL,
    rank_position INT NOT NULL,
    score DECIMAL(19, 4) NOT NULL,
    view_count BIGINT NOT NULL,
    like_count BIGINT NOT NULL,
    sales_count BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_rank_weekly_product UNIQUE (period_start, product_id),
    CONSTRAINT uk_product_rank_weekly_position UNIQUE (period_start, rank_position)
) ENGINE=InnoDB;

CREATE TABLE mv_product_rank_monthly (
    id BIGINT NOT NULL AUTO_INCREMENT,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    product_id BIGINT NOT NULL,
    rank_position INT NOT NULL,
    score DECIMAL(19, 4) NOT NULL,
    view_count BIGINT NOT NULL,
    like_count BIGINT NOT NULL,
    sales_count BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_rank_monthly_product UNIQUE (period_start, product_id),
    CONSTRAINT uk_product_rank_monthly_position UNIQUE (period_start, rank_position)
) ENGINE=InnoDB;

CREATE TABLE product_rank_job_lock (
    id BIGINT NOT NULL AUTO_INCREMENT,
    period_type VARCHAR(10) NOT NULL,
    period_start DATE NOT NULL,
    owner_id BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_rank_job_lock_period UNIQUE (period_type, period_start)
) ENGINE=InnoDB;
