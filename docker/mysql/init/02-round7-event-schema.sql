USE loopers;

DROP PROCEDURE IF EXISTS loopers_add_column_if_missing;
DROP PROCEDURE IF EXISTS loopers_add_index_if_missing;
DROP PROCEDURE IF EXISTS loopers_execute_if_column_exists;

DELIMITER //

CREATE PROCEDURE loopers_add_column_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_definition TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN ', p_column_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

CREATE PROCEDURE loopers_add_index_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_index_definition TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD ', p_index_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

CREATE PROCEDURE loopers_execute_if_column_exists(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DELIMITER ;

CREATE TABLE IF NOT EXISTS coupon_issue_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    coupon_template_id BIGINT NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    status ENUM('PENDING', 'SUCCEEDED', 'FAILED') NOT NULL,
    issued_coupon_id BIGINT NULL,
    failure_reason VARCHAR(255) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_coupon_issue_request_user_created (user_id, created_at),
    INDEX idx_coupon_issue_request_template_status (coupon_template_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS event_handled (
    event_id VARCHAR(64) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    handled_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS product_metrics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    like_count BIGINT NOT NULL,
    sales_count BIGINT NOT NULL,
    view_count BIGINT NOT NULL,
    last_like_event_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_metrics_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS order_event_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    partition_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status ENUM('PENDING', 'SENT', 'FAILED') NOT NULL,
    retry_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_outbox_event_id (event_id),
    INDEX idx_event_outbox_status_created (status, created_at),
    INDEX idx_event_outbox_topic_partition (topic, partition_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CALL loopers_add_column_if_missing('coupon_template', 'total_issue_limit', '`total_issue_limit` BIGINT NULL AFTER `min_order_amount`');

CALL loopers_add_column_if_missing('order_event_outbox', 'event_id', '`event_id` VARCHAR(64) NULL AFTER `id`');
CALL loopers_add_column_if_missing('order_event_outbox', 'topic', '`topic` VARCHAR(255) NULL AFTER `event_id`');
CALL loopers_add_column_if_missing('order_event_outbox', 'partition_key', '`partition_key` VARCHAR(255) NULL AFTER `topic`');
CALL loopers_add_column_if_missing('order_event_outbox', 'aggregate_type', '`aggregate_type` VARCHAR(255) NULL AFTER `event_type`');
CALL loopers_add_column_if_missing('order_event_outbox', 'aggregate_id', '`aggregate_id` VARCHAR(255) NULL AFTER `aggregate_type`');

UPDATE order_event_outbox
SET event_id = UUID()
WHERE event_id IS NULL;

UPDATE order_event_outbox
SET topic = 'order-events'
WHERE topic IS NULL;

CALL loopers_execute_if_column_exists(
    'order_event_outbox',
    'order_id',
    'UPDATE order_event_outbox SET partition_key = CAST(order_id AS CHAR) WHERE partition_key IS NULL'
);

UPDATE order_event_outbox
SET aggregate_type = 'ORDER'
WHERE aggregate_type IS NULL;

CALL loopers_execute_if_column_exists(
    'order_event_outbox',
    'order_id',
    'UPDATE order_event_outbox SET aggregate_id = CAST(order_id AS CHAR) WHERE aggregate_id IS NULL'
);

UPDATE order_event_outbox
SET partition_key = aggregate_id
WHERE partition_key IS NULL
  AND aggregate_id IS NOT NULL;

CALL loopers_add_index_if_missing('order_event_outbox', 'uk_event_outbox_event_id', 'UNIQUE KEY `uk_event_outbox_event_id` (`event_id`)');
CALL loopers_add_index_if_missing('order_event_outbox', 'idx_event_outbox_status_created', 'INDEX `idx_event_outbox_status_created` (`status`, `created_at`)');
CALL loopers_add_index_if_missing('order_event_outbox', 'idx_event_outbox_topic_partition', 'INDEX `idx_event_outbox_topic_partition` (`topic`, `partition_key`)');

DROP PROCEDURE loopers_add_column_if_missing;
DROP PROCEDURE loopers_add_index_if_missing;
DROP PROCEDURE loopers_execute_if_column_exists;
