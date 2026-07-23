package com.loopers.batch.job.ranking.dto;

// Reader 출력 DTO. product_metrics_daily 를 기간·상품별로 GROUP BY 하여 합산한 결과 한 행이다.
public record ProductMetricPeriodSum(long productId, long viewCount, long likeCount, long orderCount) {
}
