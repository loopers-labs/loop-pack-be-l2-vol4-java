package com.loopers.brand.interfaces.api;

import com.loopers.brand.application.BrandInfo;

public record BrandResponse(Long id, String name, String description) {
    public static BrandResponse from(BrandInfo info) {
        return new BrandResponse(info.id(), info.name(), info.description());
    }
}
