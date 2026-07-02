package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;

public record BrandInfo(Long id, String name, String description) {
    public static BrandInfo from(Brand brand) {
        return new BrandInfo(brand.getId(), brand.getName(), brand.getDescription());
    }
}
