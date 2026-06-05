package com.loopers.interfaces.api.admin.product.dto;

import com.loopers.application.product.ProductAdminInfo;

import java.time.ZonedDateTime;

public record ProductAdminV1Response(
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
    public static ProductAdminV1Response from(ProductAdminInfo info) {
        return new ProductAdminV1Response(
            info.id(),
            info.name(),
            info.description(),
            info.price(),
            info.brandId(),
            info.brandName(),
            info.likeCount(),
            info.stockQuantity(),
            info.createdAt(),
            info.updatedAt()
        );
    }
}
