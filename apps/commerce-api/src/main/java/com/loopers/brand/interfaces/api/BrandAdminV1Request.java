package com.loopers.brand.interfaces.api;

import com.loopers.brand.application.BrandCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class BrandAdminV1Request {

    public record Create(
        @NotBlank(message = "브랜드 이름은 필수입니다.")
        @Size(max = 50, message = "브랜드 이름은 50자 이내여야 합니다.")
        String name,

        @Size(max = 200, message = "브랜드 설명은 200자 이내여야 합니다.")
        String description,

        @Size(max = 500, message = "로고 URL은 500자 이내여야 합니다.")
        String logoUrl
    ) {
        public BrandCommand.Create toCommand() {
            return new BrandCommand.Create(name, description, logoUrl);
        }
    }

    public record Update(
        @NotBlank(message = "브랜드 이름은 필수입니다.")
        @Size(max = 50, message = "브랜드 이름은 50자 이내여야 합니다.")
        String name,

        @Size(max = 200, message = "브랜드 설명은 200자 이내여야 합니다.")
        String description,

        @Size(max = 500, message = "로고 URL은 500자 이내여야 합니다.")
        String logoUrl
    ) {
        public BrandCommand.Update toCommand(Long brandId) {
            return new BrandCommand.Update(brandId, name, description, logoUrl);
        }
    }
}
