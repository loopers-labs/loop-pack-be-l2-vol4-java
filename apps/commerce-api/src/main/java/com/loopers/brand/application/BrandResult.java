package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;

public class BrandResult {

    public record Detail(
        Long id,
        String name,
        String description,
        String logoUrl
    ) {
        public static Detail from(Brand brand) {
            return new Detail(brand.getId(), brand.getName(), brand.getDescription(), brand.getLogoUrl());
        }
    }
}
