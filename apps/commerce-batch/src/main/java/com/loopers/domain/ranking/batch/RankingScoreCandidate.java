package com.loopers.domain.ranking.batch;

/**
 * Top100 min-heap 누적 대상이 되는 상품별 점수 후보.
 */
public record RankingScoreCandidate(Long productId, double score) {
}
