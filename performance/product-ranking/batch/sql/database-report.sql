SET @job_execution_id = (
    SELECT MAX(je.JOB_EXECUTION_ID)
    FROM BATCH_JOB_EXECUTION AS je
    JOIN BATCH_JOB_INSTANCE AS ji
      ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID
    JOIN BATCH_JOB_EXECUTION_PARAMS AS target_date
      ON target_date.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID
     AND target_date.PARAMETER_NAME = 'targetDate'
     AND target_date.PARAMETER_VALUE = @target_date_value
    JOIN BATCH_JOB_EXECUTION_PARAMS AS rebuild
      ON rebuild.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID
     AND rebuild.PARAMETER_NAME = 'rebuildSequence'
     AND rebuild.PARAMETER_VALUE = @rebuild_sequence
    WHERE ji.JOB_NAME = 'productRankingAggregationJob'
);

SELECT 'job_execution' AS report_section;
SELECT
    je.JOB_EXECUTION_ID AS job_execution_id,
    je.JOB_INSTANCE_ID AS job_instance_id,
    je.STATUS AS status,
    je.EXIT_CODE AS exit_code,
    je.START_TIME AS started_at,
    je.END_TIME AS finished_at,
    TIMESTAMPDIFF(MICROSECOND, je.START_TIME, je.END_TIME) / 1000 AS elapsed_ms,
    SUM(se.READ_COUNT) AS total_read_count,
    SUM(se.WRITE_COUNT) AS total_write_count,
    ROUND(
        SUM(se.READ_COUNT)
        / NULLIF(TIMESTAMPDIFF(MICROSECOND, je.START_TIME, je.END_TIME) / 1000000, 0),
        2
    ) AS read_items_per_second
FROM BATCH_JOB_EXECUTION AS je
LEFT JOIN BATCH_STEP_EXECUTION AS se
  ON se.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID
WHERE je.JOB_EXECUTION_ID = @job_execution_id
GROUP BY
    je.JOB_EXECUTION_ID,
    je.JOB_INSTANCE_ID,
    je.STATUS,
    je.EXIT_CODE,
    je.START_TIME,
    je.END_TIME;

SELECT 'step_execution' AS report_section;
SELECT
    STEP_NAME AS step_name,
    STATUS AS status,
    TIMESTAMPDIFF(MICROSECOND, START_TIME, END_TIME) / 1000 AS elapsed_ms,
    READ_COUNT AS read_count,
    WRITE_COUNT AS write_count,
    COMMIT_COUNT AS commit_count,
    ROLLBACK_COUNT AS rollback_count,
    READ_SKIP_COUNT + WRITE_SKIP_COUNT + PROCESS_SKIP_COUNT AS skip_count,
    ROUND(
        READ_COUNT
        / NULLIF(TIMESTAMPDIFF(MICROSECOND, START_TIME, END_TIME) / 1000000, 0),
        2
    ) AS read_items_per_second
FROM BATCH_STEP_EXECUTION
WHERE JOB_EXECUTION_ID = @job_execution_id
ORDER BY STEP_EXECUTION_ID;

SELECT 'snapshot' AS report_section;
SELECT
    'weekly' AS period_type,
    COUNT(*) AS row_count,
    MIN(rank_position) AS minimum_rank,
    MAX(rank_position) AS maximum_rank,
    COALESCE(
        SUM(CRC32(CONCAT_WS('|', rank_position, product_id, CAST(score AS CHAR)))),
        0
    ) AS checksum
FROM mv_product_rank_weekly
WHERE aggregation_date = @target_date
UNION ALL
SELECT
    'monthly' AS period_type,
    COUNT(*) AS row_count,
    MIN(rank_position) AS minimum_rank,
    MAX(rank_position) AS maximum_rank,
    COALESCE(
        SUM(CRC32(CONCAT_WS('|', rank_position, product_id, CAST(score AS CHAR)))),
        0
    ) AS checksum
FROM mv_product_rank_monthly
WHERE aggregation_date = @target_date;

SELECT 'source' AS report_section;
SELECT
    COUNT(*) AS row_count,
    COUNT(DISTINCT metric_date) AS metric_dates,
    COUNT(DISTINCT product_id) AS products,
    MIN(metric_date) AS first_metric_date,
    MAX(metric_date) AS last_metric_date
FROM product_metrics
WHERE product_id > @product_id_base
  AND product_id <= @product_id_base + @daily_products;

SELECT 'table_size' AS report_section;
SELECT
    TABLE_NAME AS table_name,
    TABLE_ROWS AS estimated_rows,
    DATA_LENGTH AS data_bytes,
    INDEX_LENGTH AS index_bytes
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
      'product_metrics',
      'stg_product_rank_aggregation',
      'mv_product_rank_weekly',
      'mv_product_rank_monthly'
  )
ORDER BY TABLE_NAME;
