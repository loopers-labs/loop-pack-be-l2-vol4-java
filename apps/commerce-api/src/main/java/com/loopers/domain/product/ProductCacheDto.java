package com.loopers.domain.product;

import java.time.ZonedDateTime;
import java.util.UUID;

public record ProductCacheDto(
        UUID id,
        String name,
        String description,
        Long price,
        String brandName,
        long likeCount,
        ZonedDateTime createdAt,
        ZonedDateTime deletedAt
) {
    public static ProductCacheDto from(ProductModel product) {
        return new ProductCacheDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getBrand().getName(),
                product.getLikeCount(),
                product.getCreatedAt(),
                product.getDeletedAt()
        );
    }
}
