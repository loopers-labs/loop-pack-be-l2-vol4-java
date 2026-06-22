package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

import java.math.BigDecimal;

public class ProductV1Dto {
    public record CreateProductRequest(
        Long brandId,
        String name,
        BigDecimal price
    ) {}

    public record UpdateProductRequest(
        String name,
        BigDecimal price
    ) {}

    public record ProductResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        BigDecimal price,
        int likeCount,
        java.time.ZonedDateTime createdAt
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.price(),
                info.likeCount(),
                info.createdAt()
            );
        }
    }
}
