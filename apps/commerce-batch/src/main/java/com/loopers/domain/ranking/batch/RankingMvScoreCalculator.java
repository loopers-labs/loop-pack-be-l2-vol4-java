package com.loopers.domain.ranking.batch;

import org.springframework.stereotype.Component;

/**
 * 주간/월간 랭킹 배치의 점수 산식. product_daily_metrics 기간 합산 카운트를 가중합해 점수를 만든다.
 * 금액 누적치가 없어 일간 Redis 랭킹(RankingScorePolicy, 로그 정규화)과는 다른 카운트 기반 단순합을 쓴다.
 */
@Component
public class RankingMvScoreCalculator {

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.6;

    public RankingScoreCandidate calculate(RankingDailyMetricsAggregate aggregate) {
        double score = VIEW_WEIGHT * aggregate.viewCount()
            + LIKE_WEIGHT * aggregate.likeCount()
            + ORDER_WEIGHT * aggregate.orderCount();
        return new RankingScoreCandidate(aggregate.productId(), score);
    }
}
