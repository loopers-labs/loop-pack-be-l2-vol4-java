package com.loopers.batch.job.ranking;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@RequiredArgsConstructor
@Component
public class RankingBatchJobMetricsListener implements JobExecutionListener {

    private final RankingBatchJobMetrics metrics;

    @Override
    public void afterJob(@Nonnull JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            metrics.recordSuccess(Instant.now());
        } else {
            metrics.recordFailure();
        }
    }
}
