package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.ProductMetricRow;
import com.loopers.batch.job.ranking.RankAggregationJobConfig;
import com.loopers.ranking.domain.ProductRankModel;
import com.loopers.ranking.domain.RankPeriod;
import com.loopers.ranking.domain.RankingScorePolicy;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reader가 점수순(내림차순)으로 흘려준 행에 순차 rank를 부여하고, 정책으로 저장할 점수를 계산해
 * 기간(period)에 맞는 MV 엔티티로 변환한다.
 * StepScope라 Step 실행마다 새 인스턴스가 만들어져 rank 카운터가 초기화된다.
 */
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankAggregationJobConfig.JOB_NAME)
@Component
public class RankItemProcessor implements ItemProcessor<ProductMetricRow, ProductRankModel> {

    // 부동소수 오차 허용치. 정렬식↔정책식이 같으면 점수는 단조 감소해야 하며, 이 값을 넘는 역전만 오류로 본다.
    private static final double MONOTONIC_EPSILON = 1e-6;

    private final RankingScorePolicy scorePolicy;
    private final RankPeriod period;
    private final AtomicInteger rankCounter = new AtomicInteger(0);
    private double prevScore = Double.POSITIVE_INFINITY;

    public RankItemProcessor(
        RankingScorePolicy scorePolicy,
        @Value("#{jobParameters['period']}") String period
    ) {
        this.scorePolicy = scorePolicy;
        this.period = RankPeriod.from(period);
    }

    @Override
    public ProductRankModel process(ProductMetricRow row) {
        double score = scorePolicy.score(row.viewCount(), row.likeCount(), row.salesCount());
        // [fix] rank는 SQL 정렬로, score는 정책으로 따로 계산돼 두 식이 어긋나면 rank·score가 모순된 채
        //       조용히 저장된다. 흘러온 순서(점수 내림차순)를 정책 점수로 재검증해 어긋나면 배치를 실패시킨다.
        if (score > prevScore + MONOTONIC_EPSILON) {
            throw new IllegalStateException(
                "정렬식↔정책식 불일치: productId=" + row.productId()
                    + " score=" + score + " 가 직전 score=" + prevScore + " 보다 큼");
        }
        prevScore = score;
        int rank = rankCounter.incrementAndGet();
        return period.createRank(row.productId(), rank, score);
    }
}
