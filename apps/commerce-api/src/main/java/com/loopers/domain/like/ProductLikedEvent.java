package com.loopers.domain.like;

/**
 * "유저가 상품에 좋아요를 눌렀다"는 이미 일어난 사실을 통지하는 이벤트.
 * 좋아요 처리(핵심)와 무관한 후속(로깅·집계 등)은 이 이벤트를 구독해 반응한다.
 */
public record ProductLikedEvent(Long userId, Long productId) {
}
