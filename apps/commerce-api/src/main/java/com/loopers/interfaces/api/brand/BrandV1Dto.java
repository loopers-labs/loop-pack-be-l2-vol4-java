package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;

public class BrandV1Dto {

    public record ReadResponse(Long brandId, String name, String description) {

        public static ReadResponse from(BrandInfo brandInfo) {
            return new ReadResponse(brandInfo.brandId(), brandInfo.name(), brandInfo.description());
        }
    }
}
