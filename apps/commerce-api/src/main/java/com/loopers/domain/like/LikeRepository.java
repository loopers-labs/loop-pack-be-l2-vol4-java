package com.loopers.domain.like;

public interface LikeRepository {

    LikeModel save(LikeModel like);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);
}
