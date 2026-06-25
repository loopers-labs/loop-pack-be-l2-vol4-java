package com.loopers.application.product;

import com.loopers.domain.product.model.Product;

import java.time.ZonedDateTime;

public record ProductInfo(
    Long id,
    Long brandId,
    String name,
    String description,
    Long price,
    int likeCount,
    String brandName,
    boolean inStock,
    int stockQuantity,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    public static ProductInfo from(Product product) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getLikeCount(),
            null, false, 0,
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }

    public static ProductInfo of(Product product, String brandName, int stockQuantity) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getLikeCount(),
            brandName,
            stockQuantity > 0,
            stockQuantity,
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }

    public static ProductInfo of(ProductDetailCache detail, int stockQuantity) {
        return new ProductInfo(
            detail.id(),
            detail.brandId(),
            detail.name(),
            detail.description(),
            detail.price(),
            detail.likeCount(),
            detail.brandName(),
            stockQuantity > 0,
            stockQuantity,
            detail.createdAt(),
            detail.updatedAt()
        );
    }
}
