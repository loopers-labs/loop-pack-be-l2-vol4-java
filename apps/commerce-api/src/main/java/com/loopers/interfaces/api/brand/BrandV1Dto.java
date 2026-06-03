package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;

import java.time.ZonedDateTime;

public class BrandV1Dto {

    public record BrandResponse(
            Long id,
            String name,
            String description
    ) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(info.id(), info.name(), info.description());
        }
    }

    public record BrandAdminResponse(
            Long id,
            String name,
            String description,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static BrandAdminResponse from(BrandInfo info) {
            return new BrandAdminResponse(info.id(), info.name(), info.description(), info.createdAt(), info.updatedAt());
        }
    }

    public record CreateBrandRequest(
            String name,
            String description
    ) {}

    public record UpdateBrandRequest(
            String name,
            String description
    ) {}
}
