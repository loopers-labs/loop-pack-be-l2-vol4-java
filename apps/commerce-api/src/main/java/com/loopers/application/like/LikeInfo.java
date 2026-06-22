package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.product.ProductModel;

public record LikeInfo(
    Long likeId,
    Long productId,
    String productName,
    Long productPrice
) {
    public static LikeInfo of(LikeModel like, ProductModel product) {
        return new LikeInfo(
            like.getId(),
            product.getId(),
            product.getName(),
            product.getPrice()
        );
    }
}
