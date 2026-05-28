package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;

import java.time.ZonedDateTime;

public record BrandAdminInfo(
    Long id,
    String name,
    String description,
    ZonedDateTime deletedAt
) {
    public static BrandAdminInfo from(BrandModel brand) {
        return new BrandAdminInfo(
            brand.getId(),
            brand.getName(),
            brand.getDescription(),
            brand.getDeletedAt()
        );
    }
}
