package com.loopers.brand.interfaces;

import com.loopers.brand.application.BrandInfo;

public class AdminBrandV1Dto {

    public record CreateRequest(String name, String description) {}

    public record UpdateRequest(String name, String description) {}

    public record BrandResponse(Long id, String name, String description) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(info.id(), info.name(), info.description());
        }
    }
}
