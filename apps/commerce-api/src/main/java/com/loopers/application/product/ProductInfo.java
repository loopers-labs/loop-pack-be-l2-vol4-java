package com.loopers.application.product;

import com.loopers.domain.product.model.Product;

public record ProductInfo(
    Long id,
    Long brandId,
    String name,
    String description,
    Long price,
    int likeCount
) {
    public static ProductInfo from(Product product) {
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
