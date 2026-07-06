package com.loopers.domain.like;

/**
 * "유저가 상품 좋아요를 취소했다"는 이미 일어난 사실을 통지하는 이벤트.
 * 좋아요 취소 처리(핵심)와 무관한 후속(집계 -1 등)은 이 이벤트를 구독해 반응한다.
 */
public record ProductUnlikedEvent(Long userId, Long productId) {
}
