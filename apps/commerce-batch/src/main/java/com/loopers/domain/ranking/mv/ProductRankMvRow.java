package com.loopers.domain.ranking.mv;

/**
 * 검증을 통과해 MV(mv_product_rank_weekly/monthly)에 실제로 게시(publish)되는 한 행.
 */
public record ProductRankMvRow(Long productId, int rank, double score) {
}
