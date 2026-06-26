package com.loopers.domain.like.event;

/**
 * 사용자가 상품 좋아요를 취소했을 때 발행되는 이벤트.
 * LikeService 가 좋아요 row 를 삭제한 뒤 발행한다.
 */
public record ProductUnlikedEvent(Long userId, Long productId) {
}
