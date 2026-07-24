package com.loopers.domain.ranking.batch;

/**
 * product_daily_metrics를 period(주/월) 범위로 SUM한 상품 하나치 집계 결과.
 */
public record RankingDailyMetricsAggregate(Long productId, long orderCount, long likeCount, long viewCount) {
}
