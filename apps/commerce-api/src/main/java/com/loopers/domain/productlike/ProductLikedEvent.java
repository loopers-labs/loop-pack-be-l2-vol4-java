package com.loopers.domain.productlike;

/**
 * "특정 상품에 좋아요가 새로 등록되었다"는 사실을 나타내는 도메인 이벤트.
 * <p>
 * 좋아요 insert가 실제로 일어난 경우(멱등 통과)에만 발행된다. 비정규화 like_count 집계는
 * 이 이벤트의 리스너가 좋아요 트랜잭션과 분리된 별도 트랜잭션에서 수행한다.
 * <p>
 * payload에 {@code userId}를 함께 담는다. 집계 소비자는 productId만 쓰지만,
 * "누가 좋아요했다"는 사실로서의 이벤트는 유저 행동 로깅 등 다른 소비자도 재사용할 수 있다.
 */
public record ProductLikedEvent(Long productId, Long userId) {
}
