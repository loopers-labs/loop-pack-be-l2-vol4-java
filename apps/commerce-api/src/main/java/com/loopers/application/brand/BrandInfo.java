package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;

import java.time.ZonedDateTime;
import java.util.UUID;

public record BrandInfo(UUID id, String name, String description, ZonedDateTime createdAt) {

    public static BrandInfo from(BrandModel model) {
        return new BrandInfo(
            model.getId(),
            model.getName(),
            model.getDescription(),
            model.getCreatedAt()
        );
    }
}
