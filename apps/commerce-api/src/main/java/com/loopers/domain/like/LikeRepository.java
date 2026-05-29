package com.loopers.domain.like;

public interface LikeRepository {
    boolean existsBy(Long userId, Long productId);
    LikeModel save(LikeModel like);
    void deleteBy(Long userId, Long productId);
}
