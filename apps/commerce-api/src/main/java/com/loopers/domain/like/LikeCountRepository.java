package com.loopers.domain.like;

import java.util.List;
import java.util.Optional;

public interface LikeCountRepository {
    void increase(Long productId);
    void decrease(Long productId);
    Optional<ProductLikeCount> find(Long productId);
    List<ProductLikeCount> findAllByProductIds(List<Long> productIds);
    ProductLikeCount save(ProductLikeCount likeCount);
}
