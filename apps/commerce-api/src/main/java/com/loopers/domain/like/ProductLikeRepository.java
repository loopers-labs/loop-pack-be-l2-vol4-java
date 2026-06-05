package com.loopers.domain.like;

import java.util.List;

public interface ProductLikeRepository {
    ProductLike save(ProductLike productLike);
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    void deleteByUserIdAndProductId(Long userId, Long productId);
    List<ProductLike> findAllByUserId(Long userId);
}
