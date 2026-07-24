package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.ProductRankingSnapshotJobConfig;
import com.loopers.batch.job.ranking.ProductRankingSnapshotJobParameters;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.application.ProductMetricAggregate;
import com.loopers.ranking.application.ProductRankingSnapshotHeader;
import com.loopers.ranking.application.ProductRankingSnapshotRepository;
import com.loopers.ranking.application.RankingCandidate;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@StepScope
@ConditionalOnProperty(
    name = "spring.batch.job.name",
    havingValue = ProductRankingSnapshotJobConfig.JOB_NAME
)
@Component
public class ProductRankingScoreProcessor
    implements ItemProcessor<ProductMetricAggregate, RankingCandidate> {

    private final long snapshotId;
    private final RankingScorePolicy scorePolicy;

    public ProductRankingScoreProcessor(
        ProductRankingSnapshotRepository snapshotRepository,
        @Value("#{jobParameters['period']}") String period,
        @Value("#{jobParameters['aggregationEndDate']}") String aggregationEndDate,
        @Value("#{jobParameters['revision']}") Long revision
    ) {
        ProductRankingSnapshotJobParameters parameters =
            ProductRankingSnapshotJobParameters.from(period, aggregationEndDate, revision);
        ProductRankingSnapshotHeader snapshot = snapshotRepository
            .findBy(parameters.toSnapshotKey())
            .orElseThrow(() -> new IllegalStateException("product ranking snapshot not found"));
        if (snapshot.isCompleted()) {
            throw new IllegalStateException(
                "completed product ranking snapshot cannot be calculated again"
            );
        }

        this.snapshotId = snapshot.id();
        this.scorePolicy = snapshot.scorePolicy();
    }

    @Override
    public RankingCandidate process(ProductMetricAggregate aggregate) {
        double score = scorePolicy.totalScore(
            aggregate.viewCount(),
            aggregate.likeDelta(),
            aggregate.orderAmount()
        );
        return new RankingCandidate(snapshotId, aggregate.productId(), score);
    }
}
