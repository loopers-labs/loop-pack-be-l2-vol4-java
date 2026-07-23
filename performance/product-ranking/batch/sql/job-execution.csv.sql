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

SELECT 'job_execution_id,job_instance_id,status,exit_code,started_at,finished_at,elapsed_ms,total_read_count,total_write_count,read_items_per_second'
UNION ALL
SELECT CONCAT_WS(
    ',',
    je.JOB_EXECUTION_ID,
    je.JOB_INSTANCE_ID,
    je.STATUS,
    je.EXIT_CODE,
    DATE_FORMAT(je.START_TIME, '%Y-%m-%dT%H:%i:%s.%f'),
    DATE_FORMAT(je.END_TIME, '%Y-%m-%dT%H:%i:%s.%f'),
    TIMESTAMPDIFF(MICROSECOND, je.START_TIME, je.END_TIME) / 1000,
    SUM(se.READ_COUNT),
    SUM(se.WRITE_COUNT),
    ROUND(
        SUM(se.READ_COUNT)
        / NULLIF(TIMESTAMPDIFF(MICROSECOND, je.START_TIME, je.END_TIME) / 1000000, 0),
        2
    )
)
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
