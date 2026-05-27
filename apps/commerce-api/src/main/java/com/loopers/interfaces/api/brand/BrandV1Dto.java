package com.loopers.interfaces.api.brand;

import com.loopers.domain.brand.BrandModel;

public class BrandV1Dto {

    public record BrandResponse(
        Long id,
        String name
    ) {
        public static BrandResponse from(BrandModel brand) {
            return new BrandResponse(brand.getId(), brand.getName());
        }
    }
}
