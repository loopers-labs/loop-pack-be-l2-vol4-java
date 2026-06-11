package com.loopers.domain.like;

import java.util.Optional;
import java.util.List;

public interface LikeRepository {
    ProductLikeModel save(ProductLikeModel like);
    void deleteByUserIdAndProductId(Long userId, Long productId);
    List<ProductLikeModel> findAllByUserId(Long userId);
    Optional<ProductLikeModel> findByUserIdAndProductId(Long userId, Long productId);
    int countByProductId(Long productId);
}
