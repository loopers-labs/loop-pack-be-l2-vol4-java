package com.loopers.batch.job.rank;

/**
 * 집계 Reader의 출력 — 기간 내 상품별 카운트 합계. Processor가 이를 점수·순위로 환산해 MV 엔티티로 만든다.
 */
public record ProductMetricsAggregate(Long productId, long viewSum, long likeSum, long salesSum) {
}
