package com.loopers.domain.like.event;

/**
 * 사용자가 상품에 좋아요를 등록했을 때 발행되는 이벤트.
 * LikeService 가 좋아요 row 를 저장한 뒤 발행하고, 같은 트랜잭션 commit 직후에
 * read-model (ProductLikeStat) 갱신 핸들러가 받아 처리한다.
 */
public record ProductLikedEvent(Long userId, Long productId) {
}
