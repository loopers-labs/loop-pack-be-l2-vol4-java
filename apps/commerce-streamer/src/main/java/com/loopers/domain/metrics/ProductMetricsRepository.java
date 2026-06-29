package com.loopers.domain.metrics;

public interface ProductMetricsRepository {

    /**
     * 상품의 좋아요 수에 delta(+1/-1)를 원자적으로 반영한다.
     * 행이 없으면 생성, 있으면 누적 — 동시 소비자 환경에서도 read-modify-write 경합이 없도록 DB upsert 로 처리한다.
     */
    void applyLikeDelta(Long productId, long delta);
}
