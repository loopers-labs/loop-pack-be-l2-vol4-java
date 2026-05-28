package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;

import java.time.ZonedDateTime;

public class BrandV1Dto {

    public record RegisterRequest(
        String name
    ) {}

    public record BrandResponse(
        Long id,
        String name
    ) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(info.id(), info.name());
        }
    }

    public record BrandAdminResponse(
        Long id,
        String name,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static BrandAdminResponse from(BrandInfo info) {
            return new BrandAdminResponse(info.id(), info.name(), info.createdAt(), info.updatedAt());
        }
    }
}
