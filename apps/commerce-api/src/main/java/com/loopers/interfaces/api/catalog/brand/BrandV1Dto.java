package com.loopers.interfaces.api.catalog.brand;

import com.loopers.application.catalog.brand.BrandResult;

public class BrandV1Dto {
    public record BrandResponse(
        Long id,
        String name,
        String description
    ) {
        public static BrandResponse from(BrandResult result) {
            return new BrandResponse(
                result.id(),
                result.name(),
                result.description()
            );
        }
    }
}
