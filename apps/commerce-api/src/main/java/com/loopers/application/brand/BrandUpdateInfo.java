package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;

public record BrandUpdateInfo(Long brandId) {

    public static BrandUpdateInfo from(BrandModel brand) {
        return new BrandUpdateInfo(brand.getId());
    }
}
