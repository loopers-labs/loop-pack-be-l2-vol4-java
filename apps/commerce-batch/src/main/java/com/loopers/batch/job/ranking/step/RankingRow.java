package com.loopers.batch.job.ranking.step;

/** 집계 SQL 이 낸 한 행. score 는 DB 가 이미 계산해 정렬·컷까지 마친 값이다. */
public record RankingRow(Long productId, long viewCount, long likeCount, long salesCount, double score) {
}
