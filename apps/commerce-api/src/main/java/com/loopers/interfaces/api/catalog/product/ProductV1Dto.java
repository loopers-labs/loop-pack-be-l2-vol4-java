package com.loopers.interfaces.api.catalog.product;

import com.loopers.application.catalog.product.ProductResult;
import com.loopers.domain.catalog.product.ProductStatus;

public class ProductV1Dto {
    public record ProductListItemResponse(
        Long productId,
        String name,
        Long price,
        ProductStatus status,
        String brandName,
        Long likeCount,
        boolean liked
    ) {
        public static ProductListItemResponse from(ProductResult result) {
            return new ProductListItemResponse(
                result.id(),
                result.name(),
                result.price(),
                result.status(),
                result.brandName(),
                result.likeCount(),
                result.liked()
            );
        }
    }

    public record ProductDetailResponse(
        Long productId,
        String name,
        String description,
        Long price,
        ProductStatus status,
        Integer stockQuantity,
        BrandSummaryResponse brand,
        Long likeCount,
        boolean liked
    ) {
        public static ProductDetailResponse from(ProductResult result) {
            return new ProductDetailResponse(
                result.id(),
                result.name(),
                result.description(),
                result.price(),
                result.status(),
                result.stockQuantity(),
                new BrandSummaryResponse(result.brandId(), result.brandName()),
                result.likeCount(),
                result.liked()
            );
        }
    }

    public record BrandSummaryResponse(
        Long brandId,
        String name
    ) {}
}
