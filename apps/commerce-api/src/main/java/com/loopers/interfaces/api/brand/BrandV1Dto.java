package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.ZonedDateTime;

public class BrandV1Dto {

    public record BrandResponse(
            String id,
            String name,
            String description
    ) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(info.id(), info.name(), info.description());
        }
    }

    public record BrandAdminResponse(
            String id,
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
            @Schema(example = "나이키") String name,
            @Schema(example = "세계 최고의 스포츠 브랜드") String description
    ) {}

    public record UpdateBrandRequest(
            @Schema(example = "나이키 코리아") String name,
            @Schema(example = "한국 공식 스포츠 브랜드") String description
    ) {}
}
