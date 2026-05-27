package com.loopers.interfaces.api.catalog.brand;

import com.loopers.application.catalog.brand.BrandResult;

public class BrandAdminDto {
    public record CreateBrandRequest(
        String name,
        String description
    ) {}

    public record UpdateBrandRequest(
        String name,
        String description
    ) {}

    public record BrandResponse(
        Long id,
        String name,
        String description,
        boolean active
    ) {
        public static BrandResponse from(BrandResult result) {
            return new BrandResponse(
                result.id(),
                result.name(),
                result.description(),
                result.active()
            );
        }
    }
}
