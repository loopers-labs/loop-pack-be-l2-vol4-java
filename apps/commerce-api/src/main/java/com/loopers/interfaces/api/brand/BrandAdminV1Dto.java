package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandCreateInfo;

import jakarta.validation.constraints.NotBlank;

public class BrandAdminV1Dto {

    public record CreateRequest(
        @NotBlank(message = "브랜드 이름은 null이거나 빈 값일 수 없습니다.")
        String name,

        String description
    ) {
    }

    public record CreateResponse(Long brandId) {

        public static CreateResponse from(BrandCreateInfo brandCreateInfo) {
            return new CreateResponse(brandCreateInfo.brandId());
        }
    }
}
