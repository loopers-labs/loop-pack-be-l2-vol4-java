package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

record ProductCacheDto(
    Long id,
    Long brandId,
    String name,
    BigDecimal price,
    long likeCount,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt,
    ZonedDateTime deletedAt
) {
    static ProductCacheDto from(Product product) {
        return new ProductCacheDto(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getPrice(),
            product.getLikeCount(),
            product.getCreatedAt(),
            product.getUpdatedAt(),
            product.getDeletedAt()
        );
    }

    Product toDomain() {
        return new Product(id, brandId, name, price, likeCount, createdAt, updatedAt, deletedAt);
    }
}