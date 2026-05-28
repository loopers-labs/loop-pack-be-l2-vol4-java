package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

public class ProductV1Dto {

    public record RegisterRequest(
        Long brandId,
        String name,
        String description,
        Long price,
        int initialQuantity
    ) {}

    public record ProductResponse(
        Long id,
        Long brandId,
        String name,
        String description,
        Long price,
        int likeCount
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandId(),
                info.name(),
                info.description(),
                info.price(),
                info.likeCount()
            );
        }
    }

    // 고객용 응답 (brandName, inStock 포함)
    public record ProductUserResponse(
        Long id,
        String name,
        String description,
        Long price,
        String brandName,
        int likeCount,
        boolean inStock
    ) {
        public static ProductUserResponse from(ProductInfo info) {
            return new ProductUserResponse(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                info.brandName(),
                info.likeCount(),
                info.inStock()
            );
        }
    }
}
