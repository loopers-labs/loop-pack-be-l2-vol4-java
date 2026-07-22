package com.loopers.batch.job.rank.step;

import com.loopers.batch.job.rank.MetricsAggregate;
import com.loopers.batch.job.rank.RankScorer;
import com.loopers.domain.rank.ProductRankScoreModel;
import org.springframework.batch.item.ItemProcessor;

/**
 * 기간 합산 집계(MetricsAggregate)를 가중 점수로 환산해 staging 행(ProductRankScoreModel)으로 만든다.
 */
public class RankScoreProcessor implements ItemProcessor<MetricsAggregate, ProductRankScoreModel> {

    private final String periodKey;

    public RankScoreProcessor(String periodKey) {
        this.periodKey = periodKey;
    }

    @Override
    public ProductRankScoreModel process(MetricsAggregate item) {
        double score = RankScorer.score(item.viewSum(), item.likeSum(), item.salesSum());
        return ProductRankScoreModel.of(periodKey, item.productId(), score);
    }
}