package com.loopers.domain.productlike;

import java.util.List;

public interface ProductLikeRepository {
    ProductLikeModel save(ProductLikeModel like);
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    int deleteByUserIdAndProductId(Long userId, Long productId);
    List<Long> findLikedProductIds(Long userId);
}
