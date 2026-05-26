package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

import java.util.UUID;

public class ProductV1Dto {
    public record CreateProductRequest(
        String name,
        String description,
        Long price,
        Integer stock
    ) {}

    public record UpdateProductRequest(
        String name,
        String description,
        Long price,
        Integer stock
    ) {}

    public record ProductResponse(
        UUID id,
        String name,
        String description,
        Long price,
        Integer stock
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                info.stock()
            );
        }
    }
}
