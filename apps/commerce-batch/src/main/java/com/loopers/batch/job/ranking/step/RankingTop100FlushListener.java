package com.loopers.batch.job.ranking.step;

import com.loopers.domain.ranking.batch.RankingBatchJobParameters;
import com.loopers.domain.ranking.batch.RankingStagingRepository;
import com.loopers.domain.ranking.batch.RankingTop100Accumulator;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * aggregateStep 종료 시(afterStep) 힙에 누적된 Top100을 순위가 매겨진 채로 staging에 저장한다.
 * Step이 실패한 경우에는 저장하지 않는다.
 */
@StepScope
@RequiredArgsConstructor
@Component
public class RankingTop100FlushListener implements StepExecutionListener {

    private final RankingTop100Accumulator accumulator;
    private final RankingStagingRepository stagingRepository;

    @Value("#{jobParameters['period']}")
    private String period;

    @Value("#{jobParameters['periodKey']}")
    private String periodKey;

    @Override
    public ExitStatus afterStep(@Nonnull StepExecution stepExecution) {
        if (stepExecution.getStatus() != BatchStatus.COMPLETED) {
            return stepExecution.getExitStatus();
        }
        RankingBatchJobParameters parameters = RankingBatchJobParameters.of(period, periodKey);
        stagingRepository.saveAll(parameters.periodType(), periodKey, accumulator.drainRanked());
        return stepExecution.getExitStatus();
    }
}
