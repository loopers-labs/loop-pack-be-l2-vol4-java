package com.loopers.batch.job.ranking.step;

import com.loopers.domain.ranking.batch.RankingBatchJobParameters;
import com.loopers.domain.ranking.batch.RankingStagingRepository;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@StepScope
@RequiredArgsConstructor
@Component
public class RankingStagingCleanupTasklet implements Tasklet {

    private final RankingStagingRepository stagingRepository;

    @Value("#{jobParameters['period']}")
    private String period;

    @Value("#{jobParameters['periodKey']}")
    private String periodKey;

    @Override
    public RepeatStatus execute(@Nonnull StepContribution contribution, @Nonnull ChunkContext chunkContext) {
        RankingBatchJobParameters parameters = RankingBatchJobParameters.of(period, periodKey);
        stagingRepository.deleteByPeriod(parameters.periodType(), periodKey);
        return RepeatStatus.FINISHED;
    }
}
