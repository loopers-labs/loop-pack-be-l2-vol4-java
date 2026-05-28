package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;

import java.time.ZonedDateTime;

public record ProductAdminInfo(
    Long id,
    String name,
    String description,
    Long price,
    Long brandId,
    String brandName,
    Long likeCount,
    Integer stockQuantity,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    public static ProductAdminInfo from(ProductModel product, BrandModel brand, int stockQuantity) {
        return new ProductAdminInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice().value(),
            product.getBrandId(),
            brand.getName(),
            product.getLikeCount(),
            stockQuantity,
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }
}
