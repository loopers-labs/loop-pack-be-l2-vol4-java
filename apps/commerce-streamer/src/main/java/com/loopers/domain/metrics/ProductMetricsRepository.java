package com.loopers.domain.metrics;

public interface ProductMetricsRepository {

    /**
     * 상품의 좋아요 수에 delta(+1/-1)를 원자적으로 반영한다.
     * 행이 없으면 생성, 있으면 누적 — 동시 소비자 환경에서도 read-modify-write 경합이 없도록 DB upsert 로 처리한다.
     */
    void applyLikeDelta(Long productId, long delta);

    /**
     * 상품의 판매량에 delta(주문 수량)를 원자적으로 누적한다. 좋아요와 같은 delta 방식 — 순서 무관, 멱등으로 중복 방지.
     */
    void applySalesDelta(Long productId, long delta);

    /**
     * 상품의 조회수에 delta 를 누적한다. 조회는 유실 허용(fire-and-forget) 지표라 정확도보다 처리량을 우선한다.
     */
    void applyViewDelta(Long productId, long delta);

    /**
     * 재고 수량의 '절대 상태'를 최신 버전만 반영한다(state-overwrite).
     * delta 누적이 아니라 덮어쓰기이므로, 순서역전·재전송으로 도착한 더 낮은 version 이벤트는 무시해야 한다.
     * 멱등(event_handled)이 같은 이벤트 중복을 막는다면, 이 version 가드는 서로 다른 이벤트의 '역전'을 막는다.
     */
    void applyStockState(Long productId, long quantity, long version);
}
