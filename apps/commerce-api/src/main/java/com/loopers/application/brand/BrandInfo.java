package com.loopers.application.brand;

import java.time.ZonedDateTime;

import com.loopers.domain.brand.BrandModel;

public record BrandInfo(
    Long brandId,
    String name,
    String description,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {

    public static BrandInfo from(BrandModel brand) {
        return new BrandInfo(
            brand.getId(),
            brand.getName().value(),
            brand.getDescription(),
            brand.getCreatedAt(),
            brand.getUpdatedAt()
        );
    }
}
