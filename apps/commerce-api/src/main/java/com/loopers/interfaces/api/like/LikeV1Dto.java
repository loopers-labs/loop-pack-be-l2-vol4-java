package com.loopers.interfaces.api.like;

import com.loopers.domain.product.Product;

public class LikeV1Dto {
    public record LikedProductResponse(
        Long productId,
        String name,
        Long price,
        Long brandId
    ) {
        public static LikedProductResponse from(Product product) {
            return new LikedProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice().amount().longValue(),
                product.getBrandId()
            );
        }
    }
}
