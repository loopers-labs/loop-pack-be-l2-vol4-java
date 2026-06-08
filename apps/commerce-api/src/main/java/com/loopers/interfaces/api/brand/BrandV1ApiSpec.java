package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand V1 API", description = "브랜드 관련 API")
public interface BrandV1ApiSpec {

    @Operation(summary = "브랜드 상세 조회", description = "brandId 로 브랜드 단건 정보를 조회합니다.")
    ApiResponse<BrandV1Dto.BrandResponse> getBrand(Long brandId);
}
