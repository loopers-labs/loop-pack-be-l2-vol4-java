package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;

public record ProductInfo(
    Long id,
    Long brandId,
    String name,
    String description,
    String imageUrl,
    Long price,
    Integer stock,
    Long likesCount
) {
    public static ProductInfo from(ProductModel product) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getImageUrl(),
            product.getPrice(),
            product.getStock(),
            product.getLikesCount()
        );
    }
}
