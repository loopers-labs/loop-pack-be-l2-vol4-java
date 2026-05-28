package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

public class ProductDto {

    public record ProductResponse(
        Long id,
        String name,
        Long price,
        Long brandId,
        int likeCount
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.name(),
                info.price(),
                info.brandId(),
                info.likeCount()
            );
        }
    }
}
