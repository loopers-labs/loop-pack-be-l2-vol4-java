package com.loopers.brand.application;

import com.loopers.brand.domain.BrandModel;

public record BrandInfo(Long id, String name, String description) {
    public static BrandInfo from(BrandModel brand) {
        return new BrandInfo(brand.getId(), brand.getName(), brand.getDescription());
    }
}
