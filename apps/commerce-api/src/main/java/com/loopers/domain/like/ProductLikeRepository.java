package com.loopers.domain.like;

import java.util.List;
import java.util.Optional;

public interface ProductLikeRepository {
    ProductLike save(ProductLike productLike);
    Optional<ProductLike> find(String userLoginId, Long productId);
    List<ProductLike> findAllByUserLoginId(String userLoginId);
    void delete(ProductLike productLike);
}
