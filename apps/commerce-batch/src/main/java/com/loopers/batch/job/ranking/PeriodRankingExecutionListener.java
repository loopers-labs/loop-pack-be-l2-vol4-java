package com.loopers.batch.job.ranking;

import com.loopers.ranking.RankingPeriod;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

import java.time.LocalDate;

public class PeriodRankingExecutionListener implements JobExecutionListener {
    private final PeriodRankingLockManager lockManager;
    private final RankingPeriod period;

    public PeriodRankingExecutionListener(PeriodRankingLockManager lockManager, RankingPeriod period) {
        this.lockManager = lockManager;
        this.period = period;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        LocalDate requestDate = PeriodRankingJobSupport.requestDate(
            jobExecution.getJobParameters().getString("requestDate")
        );
        LocalDate periodStart = period.rangeOf(requestDate).start();
        lockManager.acquire(periodStart, jobExecution.getId());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String value = jobExecution.getJobParameters().getString("requestDate");
        if (value == null || value.isBlank()) {
            return;
        }
        LocalDate periodStart = period.rangeOf(PeriodRankingJobSupport.requestDate(value)).start();
        lockManager.release(periodStart, jobExecution.getId());
    }
}
