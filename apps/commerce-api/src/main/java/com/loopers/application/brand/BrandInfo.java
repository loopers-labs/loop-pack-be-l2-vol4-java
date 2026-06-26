package com.loopers.application.brand;

import com.loopers.domain.brand.BrandEntity;

import java.time.ZonedDateTime;

public record BrandInfo(
        String id,
        String name,
        String description,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static BrandInfo from(BrandEntity brand) {
        return new BrandInfo(
                brand.getId(),
                brand.getName(),
                brand.getDescription(),
                brand.getCreatedAt(),
                brand.getUpdatedAt()
        );
    }
}
