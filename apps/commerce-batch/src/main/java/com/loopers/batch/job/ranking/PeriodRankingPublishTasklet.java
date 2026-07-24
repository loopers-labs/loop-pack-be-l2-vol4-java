package com.loopers.batch.job.ranking;

import com.loopers.ranking.RankingPeriod;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

public class PeriodRankingPublishTasklet implements Tasklet {
    private final JdbcTemplate jdbcTemplate;
    private final RankingPeriod period;
    private final String targetTable;
    private final PeriodRankingLockManager lockManager;

    public PeriodRankingPublishTasklet(
        JdbcTemplate jdbcTemplate,
        RankingPeriod period,
        String targetTable,
        PeriodRankingLockManager lockManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.period = period;
        this.targetTable = targetTable;
        this.lockManager = lockManager;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String value = (String) chunkContext.getStepContext().getJobParameters().get("requestDate");
        LocalDate requestDate = PeriodRankingJobSupport.requestDate(value);
        RankingPeriod.DateRange range = period.rangeOf(requestDate);
        long jobInstanceId = chunkContext.getStepContext().getStepExecution()
            .getJobExecution().getJobInstance().getInstanceId();
        long jobExecutionId = chunkContext.getStepContext().getStepExecution().getJobExecutionId();
        String runKey = PeriodRankingJobSupport.runKey(period, requestDate, jobInstanceId);

        lockManager.renew(range.start(), jobExecutionId);
        assertLockOwnership(range.start(), jobExecutionId);
        jdbcTemplate.update("DELETE FROM " + targetTable + " WHERE period_start = ?", range.start());
        jdbcTemplate.update("""
            INSERT INTO %s
                (period_start, period_end, product_id, rank_position, score,
                 view_count, like_count, sales_count, created_at, updated_at)
            SELECT ?, ?, product_id,
                   ROW_NUMBER() OVER (ORDER BY score DESC, product_id ASC),
                   score, view_count, like_count, sales_count, NOW(6), NOW(6)
            FROM product_rank_staging
            WHERE run_key = ? AND score > 0
            ORDER BY score DESC, product_id ASC
            LIMIT 100
            """.formatted(targetTable), range.start(), range.end(), runKey);
        return RepeatStatus.FINISHED;
    }

    private void assertLockOwnership(LocalDate periodStart, long jobExecutionId) {
        var owners = jdbcTemplate.queryForList("""
            SELECT owner_id
            FROM product_rank_job_lock
            WHERE period_type = ?
              AND period_start = ?
              AND expires_at >= NOW(6)
            FOR UPDATE
            """, Long.class, period.name(), periodStart);
        if (owners.size() != 1 || owners.getFirst() != jobExecutionId) {
            throw new IllegalStateException("랭킹 publish lock 소유권이 만료되었거나 다른 실행으로 이전되었습니다.");
        }
    }
}
