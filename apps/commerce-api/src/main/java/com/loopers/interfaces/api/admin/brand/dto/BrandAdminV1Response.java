package com.loopers.interfaces.api.admin.brand.dto;

import com.loopers.application.brand.BrandAdminInfo;

import java.time.ZonedDateTime;

public record BrandAdminV1Response(
    Long id,
    String name,
    String description,
    long productCount,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    public static BrandAdminV1Response from(BrandAdminInfo info) {
        return new BrandAdminV1Response(
            info.id(),
            info.name(),
            info.description(),
            info.productCount(),
            info.createdAt(),
            info.updatedAt()
        );
    }
}
