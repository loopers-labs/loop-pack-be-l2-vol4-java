package com.loopers.batch.job.ranking;

import com.loopers.ranking.RankingPeriod;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

public class PeriodRankingStagingCleanupTasklet implements Tasklet {
    private final JdbcTemplate jdbcTemplate;
    private final RankingPeriod period;

    public PeriodRankingStagingCleanupTasklet(JdbcTemplate jdbcTemplate, RankingPeriod period) {
        this.jdbcTemplate = jdbcTemplate;
        this.period = period;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String value = (String) chunkContext.getStepContext().getJobParameters().get("requestDate");
        LocalDate requestDate = PeriodRankingJobSupport.requestDate(value);
        long jobInstanceId = chunkContext.getStepContext().getStepExecution()
            .getJobExecution().getJobInstance().getInstanceId();
        jdbcTemplate.update(
            "DELETE FROM product_rank_staging WHERE run_key = ?",
            PeriodRankingJobSupport.runKey(period, requestDate, jobInstanceId)
        );
        return RepeatStatus.FINISHED;
    }
}
