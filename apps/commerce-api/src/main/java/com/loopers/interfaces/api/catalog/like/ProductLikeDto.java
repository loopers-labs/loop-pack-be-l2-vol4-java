package com.loopers.interfaces.api.catalog.like;

import com.loopers.application.catalog.like.ProductLikeResult;
import com.loopers.domain.catalog.product.ProductStatus;

public class ProductLikeDto {
    public record ProductLikeResponse(
        Long productId,
        String name,
        Long price,
        ProductStatus status,
        String brandName,
        Long likeCount,
        boolean liked
    ) {
        public static ProductLikeResponse from(ProductLikeResult result) {
            return new ProductLikeResponse(
                result.productId(),
                result.name(),
                result.price(),
                result.status(),
                result.brandName(),
                result.likeCount(),
                result.liked()
            );
        }
    }
}
