package com.loopers.application.product;

import com.loopers.domain.product.ProductDetail;
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
    public static ProductAdminInfo from(ProductModel product, int stockQuantity) {
        return new ProductAdminInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getBrand().getId(),
            product.getBrand().getName(),
            product.getLikeCount(),
            stockQuantity,
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }

    public static ProductAdminInfo from(ProductDetail detail, int stockQuantity) {
        return new ProductAdminInfo(
            detail.product().getId(),
            detail.product().getName(),
            detail.product().getDescription(),
            detail.product().getPrice(),
            detail.brand().getId(),
            detail.brand().getName(),
            detail.product().getLikeCount(),
            stockQuantity,
            detail.product().getCreatedAt(),
            detail.product().getUpdatedAt()
        );
    }
}
