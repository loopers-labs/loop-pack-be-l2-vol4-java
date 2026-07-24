package com.loopers.batch.job.rank;

/**
 * Reader가 product_metrics를 기간 집계해 뽑아온 한 상품의 순위 행.
 * score/ranking은 DB(SQL)에서 가중합·ROW_NUMBER로 계산된 값이다.
 */
public record AggregatedRankRow(
    Long productId,
    long likeSum,
    long salesSum,
    double score,
    int ranking
) {
}
