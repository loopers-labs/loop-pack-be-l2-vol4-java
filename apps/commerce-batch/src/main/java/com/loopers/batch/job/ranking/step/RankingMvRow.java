package com.loopers.batch.job.ranking.step;

/** MV 에 적재할 한 행. 집계 결과에 기간 키를 붙인 것이다. */
public record RankingMvRow(String periodKey, Long productId, double score,
                           long viewCount, long likeCount, long salesCount) {
}
