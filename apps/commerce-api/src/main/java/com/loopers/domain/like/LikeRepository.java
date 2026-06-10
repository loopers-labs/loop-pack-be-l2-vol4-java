package com.loopers.domain.like;

public interface LikeRepository {
    LikeModel save(LikeModel like);
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    int deleteByUserIdAndProductId(Long userId, Long productId);
}
