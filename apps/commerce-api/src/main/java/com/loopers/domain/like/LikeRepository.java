package com.loopers.domain.like;

import org.springframework.data.domain.Page;

import com.loopers.domain.product.projection.ProductSummary;

public interface LikeRepository {

    LikeModel save(LikeModel like);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);

    Page<ProductSummary> findLikedProductSummaries(Long userId, int page, int size);
}
