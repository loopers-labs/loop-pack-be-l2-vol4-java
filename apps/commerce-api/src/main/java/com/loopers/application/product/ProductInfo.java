package com.loopers.application.product;

import com.loopers.domain.product.ProductCacheDto;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;

import java.time.ZonedDateTime;
import java.util.UUID;

public record ProductInfo(
    UUID id,
    String name,
    String description,
    Long price,
    String brandName,
    long likeCount,
    int totalQuantity,
    int reservedQuantity,
    int availableQuantity,
    ZonedDateTime createdAt,
    ZonedDateTime deletedAt
) {
    public static ProductInfo from(ProductModel product, StockModel stock) {
        return new ProductInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getBrand().getName(),
            product.getLikeCount(),
            stock.getTotalQuantity(),
            stock.getReservedQuantity(),
            stock.getAvailableQuantity(),
            product.getCreatedAt(),
            product.getDeletedAt()
        );
    }

    public static ProductInfo from(ProductCacheDto snapshot, StockModel stock) {
        return new ProductInfo(
            snapshot.id(),
            snapshot.name(),
            snapshot.description(),
            snapshot.price(),
            snapshot.brandName(),
            snapshot.likeCount(),
            stock.getTotalQuantity(),
            stock.getReservedQuantity(),
            stock.getAvailableQuantity(),
            snapshot.createdAt(),
            snapshot.deletedAt()
        );
    }
}
