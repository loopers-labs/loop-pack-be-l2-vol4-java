package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand V1 API", description = "Loopers 브랜드 대고객 도메인 API 입니다.")
public interface BrandV1ApiSpec {

    @Operation(
        summary = "브랜드 조회",
        description = "회원 또는 비회원이 특정 브랜드의 식별자·이름·설명을 조회한다."
    )
    ApiResponse<BrandV1Dto.ReadResponse> readBrand(Long brandId);
}
