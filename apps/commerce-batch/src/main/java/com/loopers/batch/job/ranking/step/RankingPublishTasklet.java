package com.loopers.batch.job.ranking.step;

import com.loopers.domain.ranking.batch.RankingBatchJobParameters;
import com.loopers.domain.ranking.batch.RankingStagingRankRow;
import com.loopers.domain.ranking.batch.RankingStagingRepository;
import com.loopers.domain.ranking.batch.RankingStagingSnapshotValidator;
import com.loopers.domain.ranking.mv.ProductRankMvPublishRepository;
import com.loopers.domain.ranking.mv.ProductRankMvRow;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * staging 스냅샷을 검증하고 통과하면 MV(weekly/monthly)에 게시한다. 검증 실패 시 예외를
 * 던져 Job을 실패시키므로, 기존 MV는 그대로 유지되고 반쯤 쓴 데이터가 노출되지 않는다(§2.5).
 */
@StepScope
@RequiredArgsConstructor
@Component
public class RankingPublishTasklet implements Tasklet {

    private final RankingStagingRepository stagingRepository;
    private final RankingStagingSnapshotValidator validator;
    private final ProductRankMvPublishRepository publishRepository;

    @Value("#{jobParameters['period']}")
    private String period;

    @Value("#{jobParameters['periodKey']}")
    private String periodKey;

    @Override
    public RepeatStatus execute(@Nonnull StepContribution contribution, @Nonnull ChunkContext chunkContext) {
        RankingBatchJobParameters parameters = RankingBatchJobParameters.of(period, periodKey);
        List<RankingStagingRankRow> staged = stagingRepository.findByPeriod(parameters.periodType(), periodKey);
        validator.validateOrThrow(staged);

        List<ProductRankMvRow> rows = staged.stream()
            .map(row -> new ProductRankMvRow(row.productId(), row.rank(), row.score()))
            .toList();
        ZonedDateTime publishedAt = ZonedDateTime.now();

        switch (parameters.periodType()) {
            case WEEKLY -> publishRepository.replaceWeeklyPeriod(periodKey, rows, publishedAt);
            case MONTHLY -> publishRepository.replaceMonthlyPeriod(periodKey, rows, publishedAt);
        }
        return RepeatStatus.FINISHED;
    }
}
