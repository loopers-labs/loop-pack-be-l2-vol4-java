package com.loopers.domain.like;

import java.util.List;

public interface LikeRepository {
    LikeModel save(LikeModel like);
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    void deleteByUserIdAndProductId(Long userId, Long productId);
    List<LikeModel> findAllByUserId(Long userId);
}
