package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

import java.math.BigDecimal;

public class ProductV1Dto {
    public record ProductResponse(
        Long id,
        String brandName,
        String name,
        BigDecimal price,
        Long likeCount,
        boolean inStock
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandName(),
                info.name(),
                info.price(),
                info.likeCount(),
                info.inStock()
            );
        }
    }
}
