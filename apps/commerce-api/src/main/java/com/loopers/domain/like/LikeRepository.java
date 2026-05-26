package com.loopers.domain.like;

public interface LikeRepository {

    /**
     * (userId, productId) Like 가 존재하지 않을 때만 새로 저장한다.
     *
     * @return 신규로 저장되었으면 true, 이미 존재해서 저장하지 않았으면 false
     */
    boolean saveIfAbsent(LikeModel like);

    /**
     * (userId, productId) Like 가 존재하면 삭제한다.
     *
     * @return 삭제된 행 수 (0 또는 1)
     */
    int deleteByUserIdAndProductId(Long userId, Long productId);
}
