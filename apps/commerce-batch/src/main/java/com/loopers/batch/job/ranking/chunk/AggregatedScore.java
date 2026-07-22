package com.loopers.batch.job.ranking.chunk;

/**
 * Chunk 잡의 ItemReader 산출물 — daily 를 상품별로 롤업한 (상품, 점수). 아직 rank 는 없다(Processor 가 부여).
 */
public record AggregatedScore(long productId, double score) {
}
