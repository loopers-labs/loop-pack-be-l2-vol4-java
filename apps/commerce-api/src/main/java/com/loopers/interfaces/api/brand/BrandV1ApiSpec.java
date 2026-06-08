package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand V1 API", description = "Loopers 브랜드 API 입니다.")
public interface BrandV1ApiSpec {

    @Operation(
        summary = "브랜드 조회",
        description = "브랜드 ID 로 브랜드 정보를 조회합니다."
    )
    ApiResponse<BrandV1Dto.BrandResponse> getBrand(
        @Parameter(name = "brandId", in = ParameterIn.PATH, required = true, description = "브랜드 ID")
        Long brandId
    );
}
