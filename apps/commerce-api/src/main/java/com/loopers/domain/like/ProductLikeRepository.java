package com.loopers.domain.like;

public interface ProductLikeRepository {

    /**
     * 좋아요가 없을 때만 저장한다. 새로 저장되면 true, 이미 존재하면 false 를 반환한다. 동시 요청에서도 (user_id,
     * product_id) 유니크 제약으로 최대 한 건만 저장됨을 보장한다.
     */
    boolean saveIfAbsent(String userId, Long productId);

    /**
     * 좋아요를 삭제하고 삭제된 행 수를 반환한다. 반환값이 1일 때만 카운트를 감소시켜 이중 감소를 방지한다.
     */
    int delete(String userId, Long productId);
}
