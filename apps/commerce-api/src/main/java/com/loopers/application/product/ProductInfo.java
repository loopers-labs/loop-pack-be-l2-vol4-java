package com.loopers.application.product;

import com.loopers.domain.brand.BrandEntity;
import com.loopers.domain.inventory.InventoryEntity;
import com.loopers.domain.product.ProductEntity;

import java.time.ZonedDateTime;

public record ProductInfo(
    Long id,
    Long brandId,
    String brandName,
    String name,
    String description,
    Long price,
    Long likeCount,
    Integer quantity,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    public static ProductInfo from(ProductEntity product, BrandEntity brand, InventoryEntity inventory) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            brand.getName(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getLikeCount(),
            inventory.getQuantity(),
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }
}
