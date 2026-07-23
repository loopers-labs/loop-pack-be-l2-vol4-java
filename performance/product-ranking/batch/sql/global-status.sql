SELECT
    VARIABLE_NAME,
    VARIABLE_VALUE
FROM performance_schema.global_status
WHERE VARIABLE_NAME IN (
    'Connections',
    'Created_tmp_disk_tables',
    'Created_tmp_tables',
    'Handler_read_next',
    'Handler_read_rnd_next',
    'Innodb_buffer_pool_read_requests',
    'Innodb_buffer_pool_reads',
    'Innodb_data_fsyncs',
    'Innodb_data_reads',
    'Innodb_data_writes',
    'Innodb_log_waits',
    'Innodb_os_log_written',
    'Questions',
    'Rows_affected',
    'Rows_read',
    'Rows_sent',
    'Threads_running'
)
ORDER BY VARIABLE_NAME;
