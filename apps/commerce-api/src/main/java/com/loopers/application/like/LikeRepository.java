package com.loopers.application.like;

import com.loopers.domain.like.ProductLikeModel;

import java.util.Optional;
import java.util.List;

public interface LikeRepository {
    ProductLikeModel save(ProductLikeModel like);
    void deleteByUserIdAndProductId(Long userId, Long productId);
    void delete(ProductLikeModel like);
    List<ProductLikeModel> findAllByUserId(Long userId);
    Optional<ProductLikeModel> findByUserIdAndProductId(Long userId, Long productId);
    int countByProductId(Long productId);
    java.util.Map<Long, Integer> countByProductIds(List<Long> productIds);
}
