package com.loopers.application.product;

import com.loopers.application.product.ProductInfo.BrandSummary;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;

import java.time.ZonedDateTime;

public record ProductAdminInfo(
    Long id,
    String name,
    String description,
    Long price,
    int stockQuantity,
    long likeCount,
    BrandSummary brand,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt,
    ZonedDateTime deletedAt
) {
    public static ProductAdminInfo from(ProductModel product, StockModel stock, BrandModel brand) {
        return new ProductAdminInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            stock.getQuantity(),
            product.getLikeCount(),
            BrandSummary.from(brand),
            product.getCreatedAt(),
            product.getUpdatedAt(),
            product.getDeletedAt()
        );
    }
}
