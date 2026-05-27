package com.loopers.domain.like;

import java.util.List;
import java.util.Map;

public interface LikeRepository {
    LikeModel save(LikeModel like);
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    void deleteByUserIdAndProductId(Long userId, Long productId);
    List<LikeModel> findAllByUserId(Long userId);
    long countByProductId(Long productId);
    Map<Long, Long> countAllByProductIdIn(List<Long> productIds);
}
