package com.loopers.like.interfaces.api;

import com.loopers.like.application.LikeResult;
import com.loopers.product.domain.ProductStatus;

public class LikeV1Response {

    public record LikedProduct(
        Long productId,
        Long brandId,
        String name,
        String description,
        long price,
        ProductStatus status
    ) {
        public static LikedProduct from(LikeResult.LikedProduct result) {
            return new LikedProduct(
                result.productId(),
                result.brandId(),
                result.name(),
                result.description(),
                result.price(),
                result.status()
            );
        }
    }
}
