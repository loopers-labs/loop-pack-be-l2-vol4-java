package com.loopers.application.product;

import com.loopers.domain.product.ProductEntity;

public record ProductInfo(
    Long id,
    Long brandId,
    String name,
    String description,
    Long price,
    Long likeCount
) {
    public static ProductInfo from(ProductEntity product) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getLikeCount()
        );
    }
}
