package com.loopers.batch.job.ranking.chunk;

/**
 * Chunk 잡의 ItemProcessor 산출물 — MV 한 행. Processor 가 Reader 순서(score DESC)대로 rank 를 부여한 결과.
 */
public record RankedRow(String yearWeek, long productId, double score, int rankNo) {
}
