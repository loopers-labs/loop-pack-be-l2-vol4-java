package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.product.ProductModel;

import java.time.ZonedDateTime;

public record LikeInfo(
    Long productId,
    String productName,
    int price,
    String brandName,
    ZonedDateTime likedAt
) {
    public static LikeInfo from(LikeModel like, ProductModel product) {
        return new LikeInfo(
            product.getId(),
            product.getName(),
            product.getPrice(),
            product.getBrand().getName(),
            like.getLikedAt()
        );
    }
}
