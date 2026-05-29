package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.stock.ProductStock;

import java.time.ZonedDateTime;

public record ProductInfo(
    Long id,
    Long brandId,
    String name,
    String description,
    long price,
    int stockQuantity,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt,
    ZonedDateTime deletedAt
) {

    public static ProductInfo from(Product product, ProductStock productStock) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            productStock.getQuantity(),
            product.getCreatedAt(),
            product.getUpdatedAt(),
            product.getDeletedAt()
        );
    }
}
