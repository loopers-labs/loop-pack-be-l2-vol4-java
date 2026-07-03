package com.loopers.domain.productlike;

/**
 * "특정 상품의 좋아요가 취소되었다"는 사실을 나타내는 도메인 이벤트.
 * <p>
 * 좋아요 delete가 실제로 일어난 경우(1행 삭제)에만 발행된다. 비정규화 like_count 감소는
 * 이 이벤트의 리스너가 좋아요 트랜잭션과 분리된 별도 트랜잭션에서 수행한다.
 * <p>
 * {@link ProductLikedEvent}와 대칭으로 {@code userId}를 함께 담는다.
 */
public record ProductUnlikedEvent(Long productId, Long userId) {
}
