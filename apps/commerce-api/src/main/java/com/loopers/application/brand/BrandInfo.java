package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;

public record BrandInfo(
        Long id,
        String name,
        String status
) {
    public static BrandInfo from(BrandModel brand) {
        return new BrandInfo(
                brand.getId(),
                brand.getName(),
                brand.getStatus().getDescription()
        );
    }
}