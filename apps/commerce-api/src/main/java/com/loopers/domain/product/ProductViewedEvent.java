package com.loopers.domain.product;

/**
 * 상품 상세가 조회됐음을 알리는 도메인 이벤트. 조회수 집계(product_metrics.view_count += 1)의 트리거다.
 * outbox → catalog-events 로 발행되고, commerce-streamer 가 PRODUCT_VIEWED 로 소비해 +1 집계한다.
 * 조회수는 가산이라 version 은 최신성 비교에 쓰이지 않는다.
 */
public record ProductViewedEvent(Long productId) {
    public static ProductViewedEvent of(Long productId) {
        return new ProductViewedEvent(productId);
    }
}
