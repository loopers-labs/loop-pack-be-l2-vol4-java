package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import jakarta.validation.constraints.NotBlank;

public class BrandAdminV1Dto {

    public record BrandResponse(Long id, String name, String description) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(info.id(), info.name(), info.description());
        }
    }

    public record CreateBrandRequest(
        @NotBlank String name,
        @NotBlank String description
    ) {}

    public record UpdateBrandRequest(
        @NotBlank String name,
        @NotBlank String description
    ) {}
}
