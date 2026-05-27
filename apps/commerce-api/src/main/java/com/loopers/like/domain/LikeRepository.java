package com.loopers.like.domain;

import java.util.List;
import java.util.Optional;

public interface LikeRepository {
    LikeModel save(LikeModel like);
    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);
    List<LikeModel> findAllByUserId(Long userId);
    void delete(LikeModel like);
}
