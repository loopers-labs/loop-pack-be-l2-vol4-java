package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;

public class BrandDto {

    public record BrandResponse(Long id, String name) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(info.id(), info.name());
        }
    }

    public record CreateBrandRequest(String name) {}

    public record UpdateBrandRequest(String name) {}
}
