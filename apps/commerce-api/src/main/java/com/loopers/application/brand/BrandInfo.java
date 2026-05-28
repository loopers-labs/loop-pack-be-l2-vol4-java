package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;

public record BrandInfo(Long id, String name, String description) {
    public static BrandInfo from(BrandModel model) {
        return new BrandInfo(model.getId(), model.getName(), model.getDescription());
    }
}
