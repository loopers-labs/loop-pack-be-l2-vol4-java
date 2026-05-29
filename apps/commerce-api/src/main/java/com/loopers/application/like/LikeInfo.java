package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.product.ProductModel;

public record LikeInfo(
    Long likeId,
    Long productId,
    String productName,
    Long price,
    Long likeCount
) {
    public static LikeInfo from(LikeModel like, ProductModel product, long likeCount) {
        return new LikeInfo(
            like.getId(),
            product.getId(),
            product.getName(),
            product.getPrice(),
            likeCount
        );
    }
}
