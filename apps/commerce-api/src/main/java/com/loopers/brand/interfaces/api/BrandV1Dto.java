package com.loopers.brand.interfaces.api;

import com.loopers.brand.application.BrandCommand;
import com.loopers.brand.domain.Brand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class BrandV1Dto {

    public record CreateRequest(
        @NotBlank(message = "브랜드 이름은 필수입니다.")
        @Size(max = 50, message = "브랜드 이름은 50자 이내여야 합니다.")
        String name,

        @Size(max = 200, message = "브랜드 설명은 200자 이내여야 합니다.")
        String description
    ) {
        public BrandCommand.Create toCommand() {
            return new BrandCommand.Create(name, description);
        }
    }

    public record UpdateRequest(
        @NotBlank(message = "브랜드 이름은 필수입니다.")
        @Size(max = 50, message = "브랜드 이름은 50자 이내여야 합니다.")
        String name,

        @Size(max = 200, message = "브랜드 설명은 200자 이내여야 합니다.")
        String description
    ) {
        public BrandCommand.Update toCommand(Long brandId) {
            return new BrandCommand.Update(brandId, name, description);
        }
    }

    public record BrandResponse(
        Long id,
        String name,
        String description
    ) {
        public static BrandResponse from(Brand brand) {
            return new BrandResponse(brand.getId(), brand.getName(), brand.getDescription());
        }
    }
}
