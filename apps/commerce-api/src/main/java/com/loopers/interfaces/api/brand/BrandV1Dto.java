package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;

public class BrandV1Dto {

    public record CreateBrandRequest(String name, String description, String imageUrl) {}

    public record UpdateBrandRequest(String name, String description, String imageUrl) {}

    public record BrandResponse(
        Long id,
        String name,
        String description,
        String imageUrl
    ) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(
                info.id(),
                info.name(),
                info.description(),
                info.imageUrl()
            );
        }
    }
}
