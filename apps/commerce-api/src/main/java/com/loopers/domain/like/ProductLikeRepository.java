package com.loopers.domain.like;

import java.util.List;
import java.util.Optional;

public interface ProductLikeRepository {
    ProductLikeModel save(ProductLikeModel productLike);
    Optional<ProductLikeModel> find(String userLoginId, Long productId);
    List<ProductLikeModel> findAllByUserLoginId(String userLoginId);
    void delete(ProductLikeModel productLike);
}
