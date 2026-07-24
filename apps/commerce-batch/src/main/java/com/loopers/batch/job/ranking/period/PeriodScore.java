package com.loopers.batch.job.ranking.period;

/**
 * 구간 스코어가 확정된 상품(Processor 출력 → staging Writer 입력).
 *
 * <p>순위는 여기 없다. 순위는 전 상품을 다 봐야 정해지므로 청크 스트리밍 중에는 알 수 없고,
 * Step2 가 staging 전체를 {@code ROW_NUMBER()} 로 훑어 확정한다.
 */
public record PeriodScore(
        long productId,
        double score
) {
}
