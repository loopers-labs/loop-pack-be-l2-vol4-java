package com.loopers.batch.job.ranking.period;

/**
 * 기간 구간에서 상품 하나에 대해 합산된 지표(Reader 출력).
 *
 * <p>{@code product_metrics_daily} 를 {@code metric_date BETWEEN ? AND ? GROUP BY product_id} 로 읽은
 * 한 행에 대응한다. {@code orderScoreSum} 은 이미 건별로 계산된 주문 스코어의 합이므로 여기에 다시
 * {@code log} 를 적용하지 않는다.
 */
public record PeriodMetricsSum(
        long productId,
        long viewSum,
        long likeSum,
        double orderScoreSum
) {
}
