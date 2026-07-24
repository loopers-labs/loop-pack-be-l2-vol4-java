package com.loopers.batch.job.ranking;

import com.loopers.ranking.RankingPeriod;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

public final class PeriodRankingPrepareTasklet implements Tasklet {
    private final JdbcTemplate jdbcTemplate;
    private final RankingPeriod period;
    private final PeriodRankingLockManager lockManager;
    private final int retentionDays;

    public PeriodRankingPrepareTasklet(
        JdbcTemplate jdbcTemplate,
        RankingPeriod period,
        PeriodRankingLockManager lockManager,
        int retentionDays
    ) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("랭킹 staging 보존 기한은 1일 이상이어야 합니다.");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.period = period;
        this.lockManager = lockManager;
        this.retentionDays = retentionDays;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String value = (String) chunkContext.getStepContext().getJobParameters().get("requestDate");
        LocalDate requestDate = PeriodRankingJobSupport.requestDate(value);
        RankingPeriod.DateRange range = period.rangeOf(requestDate);
        JobExecution jobExecution = chunkContext.getStepContext().getStepExecution().getJobExecution();
        long jobInstanceId = jobExecution.getJobInstance().getInstanceId();
        String runKey = PeriodRankingJobSupport.runKey(period, requestDate, jobInstanceId);

        lockManager.renew(range.start(), jobExecution.getId());
        deleteExpiredOrphanStaging();
        jdbcTemplate.update("DELETE FROM product_rank_staging WHERE run_key = ?", runKey);
        jdbcTemplate.update("""
            INSERT INTO product_rank_staging
                (run_key, period_type, period_start, job_instance_id,
                 product_id, score, view_count, like_count, sales_count, created_at, updated_at)
            SELECT ?, ?, ?, ?, product_id, 0,
                   SUM(view_count), SUM(like_count), SUM(sales_count), NOW(6), NOW(6)
            FROM product_metric_hourly
            WHERE metric_date BETWEEN ? AND ?
            GROUP BY product_id
            """, runKey, period.name(), range.start(), jobInstanceId, range.start(), range.end());
        lockManager.renew(range.start(), jobExecution.getId());
        return RepeatStatus.FINISHED;
    }

    private void deleteExpiredOrphanStaging() {
        jdbcTemplate.update("""
            DELETE staging
            FROM product_rank_staging staging
            WHERE staging.created_at < TIMESTAMPADD(DAY, ?, NOW(6))
              AND NOT EXISTS (
                  SELECT 1
                  FROM product_rank_job_lock job_lock
                  WHERE job_lock.expires_at >= NOW(6)
                    AND job_lock.period_type = staging.period_type
                    AND job_lock.period_start = staging.period_start
              )
            """, -retentionDays);
    }
}
