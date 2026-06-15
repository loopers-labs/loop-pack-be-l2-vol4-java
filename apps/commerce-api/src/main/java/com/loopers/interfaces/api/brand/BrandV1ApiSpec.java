package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand V1 API", description = "Loopers 브랜드 대고객 API 입니다.")
public interface BrandV1ApiSpec {

    @Operation(
            summary = "브랜드 단건 조회",
            description = "브랜드 식별자(brandId)로 브랜드 정보를 반환합니다."
    )
    ApiResponse<BrandV1Dto.BrandResponse> getBrand(Long brandId);
}
