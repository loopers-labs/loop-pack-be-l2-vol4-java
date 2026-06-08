package com.loopers.interfaces.apiadmin.brand;

import com.loopers.application.brand.BrandInfo;
import jakarta.validation.constraints.NotBlank;

public class BrandAdminV1Dto {

    public record RegisterRequest(
            @NotBlank(message = "브랜드 이름은 빈값이 들어올 수 없습니다.") String name
    ) {}

    public record UpdateRequest(
            @NotBlank(message = "브랜드 이름은 빈값이 들어올 수 없습니다.") String name
    ) {}

    public record BrandResponse(
            Long id,
            String name,
            String status
    ) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(info.id(), info.name(), info.status());
        }
    }
}
