package com.loopers.batch.job.ranking;

import com.loopers.ranking.RankingPeriod;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;

import java.time.LocalDate;

public final class PeriodRankingLeaseRenewalListener implements ChunkListener {
    private final PeriodRankingLockManager lockManager;
    private final RankingPeriod period;

    public PeriodRankingLeaseRenewalListener(PeriodRankingLockManager lockManager, RankingPeriod period) {
        this.lockManager = lockManager;
        this.period = period;
    }

    @Override
    public void beforeChunk(ChunkContext context) {
        String value = (String) context.getStepContext().getJobParameters().get("requestDate");
        LocalDate periodStart = period.rangeOf(PeriodRankingJobSupport.requestDate(value)).start();
        lockManager.renew(
            periodStart,
            context.getStepContext().getStepExecution().getJobExecutionId()
        );
    }
}
