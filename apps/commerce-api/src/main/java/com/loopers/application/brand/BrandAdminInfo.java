package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;

import java.time.ZonedDateTime;

public record BrandAdminInfo(
    Long id,
    String name,
    String description,
    long productCount,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    public static BrandAdminInfo from(BrandModel brand, long productCount) {
        return new BrandAdminInfo(
            brand.getId(),
            brand.getName(),
            brand.getDescription(),
            productCount,
            brand.getCreatedAt(),
            brand.getUpdatedAt()
        );
    }
}
