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

SELECT csv_row
FROM (
    SELECT
        0 AS sort_order,
        'step_name,status,elapsed_ms,read_count,write_count,commit_count,rollback_count,skip_count,read_items_per_second' AS csv_row
    UNION ALL
    SELECT
        STEP_EXECUTION_ID AS sort_order,
        CONCAT_WS(
            ',',
            STEP_NAME,
            STATUS,
            TIMESTAMPDIFF(MICROSECOND, START_TIME, END_TIME) / 1000,
            READ_COUNT,
            WRITE_COUNT,
            COMMIT_COUNT,
            ROLLBACK_COUNT,
            READ_SKIP_COUNT + WRITE_SKIP_COUNT + PROCESS_SKIP_COUNT,
            ROUND(
                READ_COUNT
                / NULLIF(
                    TIMESTAMPDIFF(MICROSECOND, START_TIME, END_TIME) / 1000000,
                    0
                ),
                2
            )
        ) AS csv_row
    FROM BATCH_STEP_EXECUTION
    WHERE JOB_EXECUTION_ID = @job_execution_id
) AS csv_rows
ORDER BY sort_order;
