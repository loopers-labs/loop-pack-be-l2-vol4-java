package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

public class ProductV1Dto {

    public record CreateProductRequest(
        Long brandId,
        String name,
        String description,
        Long price,
        Integer quantity
    ) {}

    public record UpdateProductRequest(
        String name,
        String description,
        Long price,
        Integer quantity
    ) {}

    public record ProductResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        String description,
        Long price,
        Long likeCount,
        Integer quantity
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.description(),
                info.price(),
                info.likeCount(),
                info.quantity()
            );
        }
    }
}
