package com.loopers.product.application.event;

/**
 * 상품 좋아요 수가 변해야 함을 알리는 통합 이벤트. delta 는 +1(등록)/-1(취소).
 * Like 도메인이 상태 전이 시 발행하고, Product 쪽 리스너가 like_count 를 원자적으로 갱신한다.
 */
public record ProductLikeChangedEvent(Long productId, long delta) {
}
