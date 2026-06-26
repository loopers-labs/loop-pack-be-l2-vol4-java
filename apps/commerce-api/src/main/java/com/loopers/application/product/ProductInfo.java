package com.loopers.application.product;

import com.loopers.domain.product.Product;

public record ProductInfo(
    Long id,
    Long brandId,
    String name,
    String description,
    Long price,
    Integer stock,
    long likeCount
) {
    public static ProductInfo from(Product product, long likeCount) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            likeCount
        );
    }
}
