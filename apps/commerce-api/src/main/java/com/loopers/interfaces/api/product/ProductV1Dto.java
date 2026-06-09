package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

public class ProductV1Dto {

    public record CreateProductRequest(
        Long brandId,
        String name,
        String description,
        Long price,
        Integer stock,
        String imageUrl
    ) {}

    public record UpdateProductRequest(
        String name,
        String description,
        Long price,
        Integer stock,
        String imageUrl
    ) {}

    public record ProductResponse(
        Long id,
        Long brandId,
        String name,
        String description,
        Long price,
        Integer stock,
        String imageUrl
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandId(),
                info.name(),
                info.description(),
                info.price(),
                info.stock(),
                info.imageUrl()
            );
        }
    }
}
