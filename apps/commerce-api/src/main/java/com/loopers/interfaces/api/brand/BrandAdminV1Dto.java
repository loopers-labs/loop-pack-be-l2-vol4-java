package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import jakarta.validation.constraints.NotBlank;

import java.time.ZonedDateTime;

public class BrandAdminV1Dto {

    public record BrandCreateRequest(
        @NotBlank(message="브랜드 이름은 필수입니다.") String name,
        @NotBlank(message="브랜드 설명은 필수입니다.") String description
    ) {}

    public record BrandUpdateRequest(
        @NotBlank(message="브랜드 이름은 필수입니다.") String name,
        @NotBlank(message="브랜드 설명은 필수입니다.")  String description
    ) {}

    public record BrandResponse(
        Long id,
        String name,
        String description,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(
                info.id(), info.name(), info.description(),
                info.createdAt(), info.updatedAt()
            );
        }
    }
}
