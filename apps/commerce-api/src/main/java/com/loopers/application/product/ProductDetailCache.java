package com.loopers.application.product;

import com.loopers.domain.product.model.Product;

import java.time.ZonedDateTime;

public record ProductDetailCache(
    Long id,
    Long brandId,
    String name,
    String description,
    Long price,
    int likeCount,
    String brandName,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    public static ProductDetailCache of(Product product, String brandName) {
        return new ProductDetailCache(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getLikeCount(),
            brandName,
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }
}
