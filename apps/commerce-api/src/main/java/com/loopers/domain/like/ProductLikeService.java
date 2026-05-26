package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;

import java.util.Optional;

public class ProductLikeService {

    public ProductLikeResult likeProduct(
        String userLoginId,
        Long productId,
        ProductModel product,
        Optional<ProductLikeModel> existingLike
    ) {
        if (existingLike.isPresent()) {
            return new ProductLikeResult(existingLike.get(), false);
        }

        ProductLikeModel productLike = new ProductLikeModel(userLoginId, productId);
        product.increaseLikeCount();
        return new ProductLikeResult(productLike, true);
    }

    public boolean unlikeProduct(ProductModel product, Optional<ProductLikeModel> existingLike) {
        if (existingLike.isEmpty()) {
            return false;
        }
        product.decreaseLikeCount();
        return true;
    }
}
