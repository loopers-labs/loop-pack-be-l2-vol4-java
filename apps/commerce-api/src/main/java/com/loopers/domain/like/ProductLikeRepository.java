package com.loopers.domain.like;

import java.util.Optional;

public interface ProductLikeRepository {
    ProductLikeModel save(ProductLikeModel productLike);
    Optional<ProductLikeModel> find(String userLoginId, Long productId);
    void delete(ProductLikeModel productLike);
}
