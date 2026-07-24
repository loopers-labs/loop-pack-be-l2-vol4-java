package com.loopers.domain.ranking;

/**
 * 기간 내 상품별 지표 합계 — Reader 가 product_metrics 를 GROUP BY product_id 로 읽어 만든 한 줄.
 *
 * <p>{@code likeDelta} 는 일간 로우의 like_delta(그날 순증감) 합이다. product_metrics.like_count 는
 * 스냅샷이라 기간 SUM 이 성립하지 않으므로 쓰지 않는다.
 */
public record ProductMetricsSum(Long productId, long likeDelta, long salesCount, long viewCount) {}
