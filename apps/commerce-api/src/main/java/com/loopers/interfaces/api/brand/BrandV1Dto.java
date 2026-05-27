package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandCreateCommand;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.brand.BrandUpdateCommand;
import jakarta.validation.constraints.NotBlank;

public class BrandV1Dto {

    public record BrandCreateRequest(
        @NotBlank String name,
        String description
    ) {
        public BrandCreateCommand toCommand() {
            return new BrandCreateCommand(name, description);
        }
    }

    public record BrandUpdateRequest(
        @NotBlank String name,
        String description
    ) {
        public BrandUpdateCommand toCommand() {
            return new BrandUpdateCommand(name, description);
        }
    }

    public record BrandResponse(Long id, String name, String description) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(info.id(), info.name(), info.description());
        }
    }
}
