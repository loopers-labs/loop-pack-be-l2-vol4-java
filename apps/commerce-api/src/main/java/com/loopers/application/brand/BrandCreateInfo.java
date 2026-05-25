package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;

public record BrandCreateInfo(Long brandId) {

    public static BrandCreateInfo from(BrandModel brand) {
        return new BrandCreateInfo(brand.getId());
    }
}
