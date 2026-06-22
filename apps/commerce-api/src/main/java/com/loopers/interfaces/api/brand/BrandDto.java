package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class BrandDto {

    public record CreateRequest(
        @NotBlank(message = "브랜드 이름은 필수입니다.")
        String name
    ) {}

    public record UpdateRequest(
        @NotBlank(message = "브랜드 이름은 필수입니다.")
        String name
    ) {}

    public record BrandResponse(Long id, String name) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(info.id(), info.name());
        }
    }

    public record BrandPageResponse(List<BrandResponse> brands, long totalElements, int totalPages) {
    }
}
