package com.loopers.batch.job.ranking;

/**
 * product_metrics에서 랭킹 점수 계산에 필요한 카운트만 읽어온 Reader 행.
 */
public record ProductMetricRow(Long productId, long viewCount, long likeCount, long salesCount) {
}
