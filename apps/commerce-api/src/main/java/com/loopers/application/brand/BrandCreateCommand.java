package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;

public record BrandCreateCommand(String name, String description) {

    public BrandModel toDomain() {
        return new BrandModel(name, description);
    }
}
