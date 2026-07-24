package com.loopers.domain.ranking.batch;

/**
 * Top100 누적 완료 후 순위가 확정된 한 행. mv_product_rank_staging에 저장되는 단위.
 */
public record RankingStagingRankRow(int rank, Long productId, double score) {
}
